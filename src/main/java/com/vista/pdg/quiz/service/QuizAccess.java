package com.vista.pdg.quiz.service;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.entity.TeachingAssignment;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.academic.repository.TeachingAssignmentRepository;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.repository.QuizRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Autorización por curso. El rol dice qué puede hacer la cuenta; la asignación docente y la
 * matrícula dicen sobre qué curso. Ninguna de las dos se toma del cliente.
 */
@Component
@RequiredArgsConstructor
public class QuizAccess {
  private final CourseRepository courses;
  private final TeachingAssignmentRepository assignments;
  private final UserRepository users;
  private final QuizRepository quizzes;

  public List<Course> managedCourses(User actor) {
    requireManager(actor);
    if (hasRole(actor, "ADMIN")) return courses.findAll(Sort.by("code"));
    return assignments.findByTeacher_IdOrderByCourse_CodeAsc(actor.getId()).stream()
        .map(TeachingAssignment::getCourse)
        .toList();
  }

  public Course requireManagedCourse(User actor, String code) {
    requireManager(actor);
    Course course =
        courses
            .findByCode(code == null ? "" : code.strip())
            .orElseThrow(() -> QuizException.invalid("courseCode", "Selecciona un curso válido"));
    if (!manages(actor, course)) throw QuizException.forbidden();
    return course;
  }

  /** 404 para un quiz inexistente y 403 para uno ajeno: el docente ya sabe que el quiz existe. */
  public Quiz requireManagedQuiz(User actor, Long quizId) {
    requireManager(actor);
    Quiz quiz = quizzes.findById(quizId).orElseThrow(QuizException::notFound);
    if (!manages(actor, quiz.getCourse())) throw QuizException.forbidden();
    return quiz;
  }

  public List<User> enrolled(Course course) {
    return users.findByCourse_IdAndRoles_NameAndEnabledTrueOrderByDisplayNameAsc(
        course.getId(), "STUDENT");
  }

  /** Curso de matrícula del estudiante; nulo si la cuenta no puede presentar quizzes. */
  public Course studentCourse(User actor) {
    return hasRole(actor, "STUDENT") ? actor.getCourse() : null;
  }

  private boolean manages(User actor, Course course) {
    return hasRole(actor, "ADMIN")
        || assignments.existsByCourse_CodeAndTeacher_Id(course.getCode(), actor.getId());
  }

  private static void requireManager(User actor) {
    if (!hasRole(actor, "ADMIN") && !hasRole(actor, "TEACHER")) throw QuizException.forbidden();
  }

  static boolean hasRole(User actor, String role) {
    return actor != null
        && actor.isEnabled()
        && actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }
}
