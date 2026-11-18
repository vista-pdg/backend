package com.vista.pdg.quiz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * HU-36: material de referencia de un quiz. Se guarda solo el texto extraído, nunca el archivo ni
 * la página original.
 */
@Entity
@Table(name = "quiz_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizMaterial {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Quiz quiz;

  /** {@code LINK} o {@code FILE}. */
  @Column(nullable = false, length = 8)
  private String kind;

  @Column(nullable = false, length = 200)
  private String name;

  /** El enlace tal como lo dio el docente; nulo para archivos. */
  @Column(length = 2048)
  private String source;

  @Column(nullable = false, length = 16)
  private String format;

  @Column(nullable = false)
  private int pages;

  @Column(nullable = false)
  private int characters;

  @Column(nullable = false)
  private boolean truncated;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
