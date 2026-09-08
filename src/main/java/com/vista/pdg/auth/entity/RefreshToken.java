package com.vista.pdg.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/**
 * Un token de refresco emitido a un usuario.
 *
 * <p>Nunca se guarda el token en claro: sólo su SHA-256. Quien tenga acceso de lectura a la tabla
 * no puede suplantar a nadie.
 *
 * <p>Los tokens se agrupan por {@code familyId}: cada login abre una familia y cada rotación añade
 * un eslabón a la misma. Si alguien presenta un token que ya fue rotado ({@code used}) o revocado,
 * significa que el token viajó a manos de un tercero, así que se revoca la familia entera. Ese es
 * el mecanismo de detección de reuso que pide la HU-08.
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
      @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true),
      @Index(name = "idx_refresh_family", columnList = "familyId")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** SHA-256 en hexadecimal del token entregado al cliente. */
  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  /** Identificador de la cadena de rotación. Compartido por todos los eslabones. */
  @Column(nullable = false, length = 36)
  private String familyId;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private Instant expiresAt;

  /** Marcado al rotarse. Un token usado que vuelve a presentarse es un reuso. */
  @Builder.Default private boolean used = false;

  /** No nulo cuando el token quedó invalidado por logout o por detección de reuso. */
  private Instant revokedAt;

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public boolean isActive(Instant now) {
    return !used && revokedAt == null && expiresAt.isAfter(now);
  }
}
