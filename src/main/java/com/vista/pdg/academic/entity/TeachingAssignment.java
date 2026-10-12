package com.vista.pdg.academic.entity;

import com.vista.pdg.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Asignación explícita; el curso de matrícula del estudiante no concede permisos docentes. */
@Entity
@Table(
    name = "teaching_assignments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "teacher_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeachingAssignment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Course course;

  @ManyToOne(optional = false)
  @JoinColumn(name = "teacher_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User teacher;
}
