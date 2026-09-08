package com.vista.pdg.telemetry.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/**
 * Un evento de generación de estructura, ya seudonimizado.
 *
 * <p>Deliberadamente <b>no</b> tiene clave foránea al usuario ni guarda correo o nombre. Quien lea
 * esta tabla —un reporte, un volcado, un docente con acceso a la analítica— ve cohortes y
 * actividad, pero no puede volver a la persona sin la clave del seudónimo, que vive fuera de la
 * base. Ese es el compromiso de privacidad que R03 exige.
 */
@Entity
@Table(
    name = "generation_events",
    indexes = {
      @Index(name = "idx_genev_course", columnList = "courseCode"),
      @Index(name = "idx_genev_pseudonym", columnList = "pseudonym"),
      @Index(name = "idx_genev_created", columnList = "createdAt")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** HMAC del identificador de cuenta, truncado. Estable por cuenta, no reversible sin la clave. */
  @Column(nullable = false, length = 32)
  private String pseudonym;

  /** Nulo para cuentas sin curso (docente, administrador). */
  @Column(length = 32)
  private String courseCode;

  @Column(length = 16)
  private String termCode;

  @Column(nullable = false, length = 32)
  private String structureType;

  @Column(length = 32)
  private String subtype;

  @Column(nullable = false)
  private int nodeCount;

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
