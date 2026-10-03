package com.vista.pdg.auth.service;

import com.vista.pdg.auth.dto.VerificationResponse;
import com.vista.pdg.auth.entity.EmailVerification;
import com.vista.pdg.auth.repository.EmailVerificationRepository;
import com.vista.pdg.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
  private final EmailVerificationRepository repository;
  private final VerificationMailer mailer;
  private final VerificationQuotaService quota;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  @Value("${auth.verification.hash-secret}")
  private String hashSecret;

  @Value("${auth.verification.ttl:PT10M}")
  private Duration ttl;

  @Value("${auth.verification.cooldown:PT1M}")
  private Duration cooldown;

  @Value("${auth.verification.email-hourly-limit:5}")
  private int emailHourlyLimit;

  @Value("${auth.verification.max-attempts:5}")
  private int maxAttempts;

  @Transactional
  public VerificationResponse send(String email) {
    Instant now = clock.instant();
    // Limpieza acotada a datos vencidos hace más de un día; nunca elimina un desafío vigente.
    repository.deleteStale(now.minus(Duration.ofDays(1)));
    EmailVerification v =
        repository
            .lockEmail(email)
            .orElseGet(
                () -> {
                  EmailVerification n = new EmailVerification();
                  n.setEmail(email);
                  n.setWindowStart(now);
                  return n;
                });
    if (v.getNextSendAt() != null && v.getNextSendAt().isAfter(now))
      throw new VerificationRateException(
          Math.max(1, Duration.between(now, v.getNextSendAt()).toSeconds() + 1));
    if (!v.getWindowStart().plus(Duration.ofHours(1)).isAfter(now)) {
      v.setWindowStart(now);
      v.setSends(0);
    }
    if (v.getSends() >= emailHourlyLimit)
      throw new VerificationRateException(
          Math.max(
              1,
              Duration.between(now, v.getWindowStart().plus(Duration.ofHours(1))).toSeconds() + 1));
    UUID id = UUID.randomUUID();
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    v.setRequestId(id);
    v.setCodeHash(hash(id, code));
    v.setExpiresAt(now.plus(ttl));
    v.setNextSendAt(now.plus(cooldown));
    v.setAttempts(0);
    v.setConsumedAt(null);
    v.setSends(v.getSends() + 1);
    repository.saveAndFlush(v);
    quota.consume();
    mailer.send(email, code, Math.max(1, ttl.toMinutes()));
    return new VerificationResponse(id, v.getExpiresAt(), v.getNextSendAt());
  }

  @Transactional(noRollbackFor = VerificationException.class)
  public void consume(String email, UUID id, String code) {
    EmailVerification v =
        repository
            .lockRequest(id)
            .orElseThrow(
                () ->
                    new VerificationException(
                        "VERIFICATION_INVALID", "El código no es válido. Solicita uno nuevo."));
    Instant now = clock.instant();
    if (v.getConsumedAt() != null)
      throw new VerificationException(
          "VERIFICATION_INVALID", "Este código ya se utilizó. Solicita uno nuevo.");
    if (!v.getExpiresAt().isAfter(now))
      throw new VerificationException(
          "VERIFICATION_EXPIRED", "El código venció. Solicita uno nuevo.");
    if (v.getAttempts() >= maxAttempts)
      throw new VerificationException(
          "VERIFICATION_ATTEMPTS", "Alcanzaste el límite de intentos. Solicita un código nuevo.");
    if (!v.getEmail().equals(email)
        || !MessageDigest.isEqual(
            v.getCodeHash().getBytes(StandardCharsets.UTF_8),
            hash(id, code).getBytes(StandardCharsets.UTF_8))) {
      v.setAttempts(v.getAttempts() + 1);
      repository.save(v);
      throw new VerificationException(
          "VERIFICATION_INVALID", "El código no es correcto. Revisa los 6 dígitos.");
    }
    v.setConsumedAt(now);
    repository.save(v);
  }

  @jakarta.annotation.PostConstruct
  void requireSafeSecret() {
    if (hashSecret.length() < 32 || (mailEnabled && hashSecret.startsWith("dev-only-")))
      throw new IllegalStateException(
          "Configura VERIFICATION_HASH_SECRET aleatorio de al menos 32 caracteres antes de habilitar correo");
  }

  @Value("${auth.verification.mail-enabled:false}")
  private boolean mailEnabled;

  private String hash(UUID id, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal(
                  ("vista-registration:" + id + ":" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("No se pudo proteger el código de verificación", ex);
    }
  }
}
