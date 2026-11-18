package com.vista.pdg.quiz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Pregunta asignada a un intento. {@code optionOrder} es la permutación con la que el estudiante ve
 * las opciones; {@code selectedOption} se guarda como índice original de la pregunta.
 */
@Entity
@Table(
    name = "quiz_attempt_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "position"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "attempt_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private QuizAttempt attempt;

  @ManyToOne(optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private QuizQuestion question;

  @Column(nullable = false)
  private int position;

  @Column(name = "option_order", nullable = false, length = 32)
  private String optionOrder;

  @Column(name = "selected_option")
  private Integer selectedOption;

  @Column(name = "answered_at")
  private Instant answeredAt;

  public List<Integer> order() {
    return Arrays.stream(optionOrder.split(",")).map(Integer::valueOf).toList();
  }

  public boolean correct() {
    return selectedOption != null && selectedOption == question.getContent().getCorrectIndex();
  }
}
