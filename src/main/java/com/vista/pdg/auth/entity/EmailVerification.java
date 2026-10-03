package com.vista.pdg.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {
  @Id
  @Column(length = 254)
  private String email;

  @Column(nullable = false, unique = true)
  private UUID requestId;

  @Column(nullable = false, length = 64)
  private String codeHash;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private Instant nextSendAt;

  @Column(nullable = false)
  private Instant windowStart;

  private int sends;
  private int attempts;
  private Instant consumedAt;
}
