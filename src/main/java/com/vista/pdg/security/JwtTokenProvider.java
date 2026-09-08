package com.vista.pdg.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Emite y valida el token de <em>acceso</em>.
 *
 * <p>El token de refresco no es un JWT y no se maneja aquí: vive en {@code RefreshTokenService},
 * persistido y revocable. La vida corta del acceso (15 minutos por defecto) es lo que acota el daño
 * de que se filtre, ya que no hay forma de revocarlo antes de que expire.
 */
@Component
public class JwtTokenProvider {

  @Value("${jwt.secret}")
  private String secret;

  @Value("${jwt.access-expiration:900000}")
  private long accessExpirationMs;

  private SecretKey key() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(
      String email, Collection<? extends GrantedAuthority> authorities) {
    Date now = new Date();
    return Jwts.builder()
        .subject(email)
        .claim("roles", authorities.stream().map(GrantedAuthority::getAuthority).toList())
        .claim("typ", "access")
        .issuedAt(now)
        .expiration(new Date(now.getTime() + accessExpirationMs))
        .signWith(key())
        .compact();
  }

  /** Vida del token de acceso en segundos, para que el cliente programe el refresco silencioso. */
  public long accessTokenTtlSeconds() {
    return accessExpirationMs / 1000;
  }

  public String extractEmail(String token) {
    return claims(token).getSubject();
  }

  public boolean isValid(String token) {
    try {
      claims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private Claims claims(String token) {
    return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
  }
}
