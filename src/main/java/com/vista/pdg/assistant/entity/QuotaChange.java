package com.vista.pdg.assistant.entity;

import com.vista.pdg.academic.entity.Course;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/**
 * Auditoría de un cambio de cuota diaria sobre un curso: qué había, qué quedó, quién y cuándo.
 *
 * <p>Guarda el correo del administrador a propósito. Es un registro de una acción administrativa,
 * no telemetría de estudiantes; aquí la trazabilidad a la persona es el requisito (CA-6), no el
 * riesgo.
 */
@Entity
@Table(
    name = "quota_changes",
    indexes = @Index(name = "idx_quota_change_course", columnList = "course_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotaChange {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  /** Nulo cuando antes regía la cuota por defecto. */
  private Integer previousQuota;

  @Column(nullable = false)
  private int newQuota;

  @Column(nullable = false, length = 160)
  private String changedBy;

  @Column(nullable = false)
  @Builder.Default
  private Instant changedAt = Instant.now();
}
