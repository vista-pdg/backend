package com.vista.pdg.quiz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Pregunta de un quiz de estudio con lo que el estudiante hizo en ella. */
@Entity
@Table(
    name = "study_questions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"study_quiz_id", "position"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyQuestion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "study_quiz_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private StudyQuiz studyQuiz;

  @Column(nullable = false)
  private int position;

  @Embedded private QuestionContent content;

  /** Índice de la opción elegida; nulo mientras no responda. */
  @Column(name = "selected_option")
  private Integer selectedOption;

  @Column(name = "answered_at")
  private Instant answeredAt;

  /** HU-36: tiempo visible de la pregunta hasta que el estudiante la comprobó. */
  @Column(name = "time_spent_ms")
  private Integer timeSpentMs;

  @Column(name = "hint_shown", nullable = false)
  private boolean hintShown;

  @Column(name = "solution_shown", nullable = false)
  private boolean solutionShown;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Respondida o con la solución a la vista: ya no admite respuesta. */
  public boolean resolved() {
    return selectedOption != null || solutionShown;
  }

  public boolean correct() {
    return selectedOption != null && selectedOption == content.getCorrectIndex();
  }
}
