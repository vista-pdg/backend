package com.vista.pdg.quiz.entity;

import com.vista.pdg.auth.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Único intento de un estudiante sobre un quiz; la unicidad la garantiza la base. */
@Entity
@Table(
    name = "quiz_attempts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"quiz_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Quiz quiz;

  @ManyToOne(optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User student;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  /** {@code min(inicio + tiempo límite, cierre del quiz)}; se fija al iniciar y no cambia. */
  @Column(nullable = false)
  private Instant deadline;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AttemptStatus status;

  /** Aciertos; nulo mientras el intento sigue abierto. */
  private Integer score;

  @Column(nullable = false)
  private int total;

  /** Incidentes del modo de prueba: foco perdido, salida de pantalla completa, copia, etc. */
  @Column(name = "integrity_events", nullable = false)
  private int integrityEvents;

  public boolean finished() {
    return status != AttemptStatus.IN_PROGRESS;
  }
}
