package com.vista.pdg.academic.entity;

import jakarta.persistence.*;
import lombok.*;

/** Periodo académico, p. ej. {@code 2026-1}. Sólo uno debe estar activo a la vez. */
@Entity
@Table(name = "academic_terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicTerm {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false, length = 16)
  private String code;

  /** El periodo al que se vinculan los registros nuevos. */
  @Builder.Default private boolean active = false;
}
