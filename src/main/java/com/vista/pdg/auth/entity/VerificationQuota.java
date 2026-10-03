package com.vista.pdg.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "verification_quotas")
@Getter
@Setter
@NoArgsConstructor
public class VerificationQuota {
  @Id private String id;

  @Column(nullable = false)
  private Instant windowStart;

  private int requests;
}
