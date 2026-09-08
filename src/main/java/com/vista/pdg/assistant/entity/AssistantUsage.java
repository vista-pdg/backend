package com.vista.pdg.assistant.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

/**
 * Mensajes al asistente consumidos por un usuario en un día calendario (zona del curso).
 *
 * <p>Persistido y no en memoria: es el contador que protege el presupuesto, y tiene que sobrevivir
 * a un reinicio. Se identifica por usuario, no por seudónimo, porque la cuota es una función de la
 * cuenta; la telemetría seudonimizada de HU-16 es otra tabla y no se cruza con esta.
 */
@Entity
@Table(
    name = "assistant_usage",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "usage_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantUsage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

  @Column(nullable = false)
  @Builder.Default
  private int count = 0;

  @Column(nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();
}
