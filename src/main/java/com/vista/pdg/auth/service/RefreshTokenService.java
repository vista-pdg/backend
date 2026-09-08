package com.vista.pdg.auth.service;

import com.vista.pdg.auth.entity.RefreshToken;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.RefreshTokenRepository;
import com.vista.pdg.exception.InvalidRefreshTokenException;
import com.vista.pdg.exception.TokenReuseDetectedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite, rota y revoca tokens de refresco.
 *
 * <p>El token de refresco es un valor opaco aleatorio, no un JWT. Un JWT de refresco no se puede
 * revocar sin una lista de invalidación, que es exactamente la tabla que tendríamos que construir
 * de todos modos; con un valor opaco la revocación es simplemente borrar o marcar la fila.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository repository;
  private final SecureRandom random = new SecureRandom();

  @Value("${jwt.refresh-expiration:604800000}")
  private long refreshExpirationMs;

  /** Resultado de emitir un token: el valor en claro (única vez que existe) y su fila. */
  public record IssuedToken(String rawToken, RefreshToken entity) {}

  /** Abre una familia nueva. Se llama en login y en registro. */
  @Transactional
  public IssuedToken issueNewFamily(User user) {
    repository.deleteExpiredTokens(user, Instant.now());
    return issue(user, UUID.randomUUID().toString());
  }

  /**
   * Rota un token de refresco dentro de su familia.
   *
   * <p>Si el token presentado ya había sido rotado o revocado, es un reuso: el mismo secreto está
   * en dos manos. No hay forma de distinguir al legítimo del atacante, así que se revoca la familia
   * completa y ambos quedan obligados a autenticarse de nuevo.
   *
   * <p>{@code noRollbackFor} es imprescindible: sin él, lanzar la excepción revierte la transacción
   * y con ella la revocación que acabamos de hacer, dejando la familia intacta. La detección
   * aparentaría funcionar —el cliente recibe 401— pero el token robado seguiría sirviendo.
   */
  @Transactional(noRollbackFor = TokenReuseDetectedException.class)
  public IssuedToken rotate(String rawToken) {
    Instant now = Instant.now();
    RefreshToken current =
        repository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new InvalidRefreshTokenException("Token de refresco desconocido"));

    // Sólo un token YA ROTADO delata un reuso: su secreto está en dos manos a la vez.
    if (current.isUsed()) {
      int revoked = repository.revokeFamily(current.getFamilyId(), now);
      log.warn(
          "Reuso de token de refresco detectado — usuario: {}, familia: {}, tokens revocados: {}",
          current.getUser().getEmail(),
          current.getFamilyId(),
          revoked);
      throw new TokenReuseDetectedException(
          "Se detectó el reuso de un token de refresco. La sesión fue revocada por seguridad.");
    }

    // Revocado pero nunca usado es el caso corriente de un logout o de una familia ya cortada. No
    // es un ataque, y presentarlo no debe devolverle al usuario una alarma de seguridad.
    if (current.getRevokedAt() != null) {
      throw new InvalidRefreshTokenException("Token de refresco revocado");
    }

    if (!current.getExpiresAt().isAfter(now)) {
      throw new InvalidRefreshTokenException("Token de refresco expirado");
    }

    current.setUsed(true);
    repository.save(current);
    return issue(current.getUser(), current.getFamilyId());
  }

  /** Cierra la sesión revocando la familia del token presentado. */
  @Transactional
  public void revokeFamilyOf(String rawToken) {
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(rt -> repository.revokeFamily(rt.getFamilyId(), Instant.now()));
  }

  private IssuedToken issue(User user, String familyId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    RefreshToken saved =
        repository.save(
            RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .familyId(familyId)
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .createdAt(Instant.now())
                .build());

    return new IssuedToken(rawToken, saved);
  }

  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
    }
  }
}
