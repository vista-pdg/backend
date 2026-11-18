package com.vista.pdg.quiz.entity;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.auth.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Quiz asignado a un curso (HU-34). El banco tiene {@code questionsPerStudent × expectedStudents}
 * preguntas; cada estudiante recibe {@code questionsPerStudent} de ellas.
 */
@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Course course;

  @Column(nullable = false, length = 160)
  private String title;

  /** Claves del catálogo de algoritmos ({@code type/subtype/operation}). */
  @Convert(converter = StringListJsonConverter.class)
  @Column(nullable = false, columnDefinition = "text")
  private List<String> topics;

  @Column(name = "questions_per_student", nullable = false)
  private int questionsPerStudent;

  @Column(name = "expected_students", nullable = false)
  private int expectedStudents;

  @Column(name = "time_limit_minutes", nullable = false)
  private int timeLimitMinutes;

  @Column(name = "opens_at", nullable = false)
  private Instant opensAt;

  @Column(name = "closes_at", nullable = false)
  private Instant closesAt;

  @Column(name = "show_grade", nullable = false)
  private boolean showGrade;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private QuizStatus status;

  /** Nulo si la cuenta que lo creó fue eliminada; el quiz sigue siendo del curso. */
  @ManyToOne
  @JoinColumn(name = "created_by")
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private User createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  /** HU-36: directivas del docente para la IA; se suman a las reglas del redactor. */
  @Column(columnDefinition = "text")
  private String instructions;

  public int bankTarget() {
    return questionsPerStudent * expectedStudents;
  }
}
