package com.vista.pdg.quiz.entity;

import com.vista.pdg.auth.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Quiz de estudio (HU-35): lo crea un estudiante para sí mismo y nadie más lo ve. No tiene curso,
 * ventana ni tiempo límite; sus preguntas salen del mismo generador que los bancos de curso.
 */
@Entity
@Table(name = "study_quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyQuiz {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User owner;

  @Column(nullable = false, length = 160)
  private String title;

  @Convert(converter = StringListJsonConverter.class)
  @Column(nullable = false, columnDefinition = "text")
  private List<String> topics;

  @Column(name = "question_count", nullable = false)
  private int questionCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
