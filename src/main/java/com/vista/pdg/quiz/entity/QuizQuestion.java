package com.vista.pdg.quiz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Pregunta del banco: escenario reproducible en VISTA, enunciado y cuatro opciones. */
@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Quiz quiz;

  @Column(name = "topic_key", nullable = false, length = 96)
  private String topicKey;

  @Column(name = "topic_label", nullable = false, length = 160)
  private String topicLabel;

  @Column(nullable = false, columnDefinition = "text")
  private String scenario;

  @Column(nullable = false, columnDefinition = "text")
  private String statement;

  @Convert(converter = StringListJsonConverter.class)
  @Column(nullable = false, columnDefinition = "text")
  private List<String> options;

  @Column(name = "correct_index", nullable = false)
  private int correctIndex;

  @Column(columnDefinition = "text")
  private String explanation;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
