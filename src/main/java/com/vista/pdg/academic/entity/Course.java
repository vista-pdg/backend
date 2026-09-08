package com.vista.pdg.academic.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Curso ofrecido en un periodo, identificado por su código institucional, p. ej. {@code CEDI-G1}.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false, length = 32)
  private String code;

  @Column(nullable = false, length = 160)
  private String name;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "term_id", nullable = false)
  private AcademicTerm term;
}
