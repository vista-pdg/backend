package com.vista.pdg.telemetry.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deriva un seudónimo estable a partir del identificador de cuenta.
 *
 * <p>Es un HMAC-SHA256 con una clave de aplicación, truncado a 16 hexadecimales (64 bits). Se usa
 * HMAC y no un hash simple porque el espacio de identificadores es pequeño y secuencial: con
 * SHA-256 a secas bastaría probar los ids del 1 en adelante para deshacer la seudonimización. La
 * clave es independiente de la de JWT a propósito: comprometer una no debe exponer la otra.
 */
@Component
public class Pseudonymizer {

  private static final int HEX_LENGTH = 16;

  private final byte[] key;

  public Pseudonymizer(@Value("${telemetry.pseudonym-secret}") String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "telemetry.pseudonym-secret debe tener al menos 32 caracteres");
    }
    this.key = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String pseudonymFor(Long userId) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] digest = mac.doFinal(String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, HEX_LENGTH);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 no disponible en esta JVM", e);
    }
  }
}
