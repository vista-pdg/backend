package com.vista.pdg.academic.service;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.entity.TeachingAssignment;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.academic.repository.TeachingAssignmentRepository;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.service.AnalyticsRequestException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeachingAssignmentService {
  private final CourseRepository courses;
  private final TeachingAssignmentRepository assignments;
  private final UserRepository users;

  public record Teacher(Long id, String displayName, String email) {}

  @Transactional(readOnly = true)
  public List<CourseDto> availableCourses(User actor) {
    requireAnalyst(actor);
    return (hasRole(actor, "ADMIN")
            ? courses.findAll(Sort.by("code"))
            : assignments.findByTeacher_IdOrderByCourse_CodeAsc(actor.getId()).stream()
                .map(TeachingAssignment::getCourse)
                .toList())
        .stream().map(CourseDto::from).toList();
  }

  @Transactional(readOnly = true)
  public Course requireAccess(User actor, String code) {
    requireAnalyst(actor);
    if (!hasRole(actor, "ADMIN")
        && !assignments.existsByCourse_CodeAndTeacher_Id(code, actor.getId())) {
      throw AnalyticsRequestException.forbidden();
    }
    return courses.findByCode(code).orElseThrow(AnalyticsRequestException::forbidden);
  }

  @Transactional(readOnly = true)
  public List<Teacher> teachers(User admin, String code) {
    requireAdmin(admin);
    requireCourse(code);
    return assignments.findByCourse_CodeOrderByTeacher_DisplayNameAsc(code).stream()
        .map(
            a ->
                new Teacher(
                    a.getTeacher().getId(),
                    a.getTeacher().getDisplayName(),
                    a.getTeacher().getEmail()))
        .toList();
  }

  @Transactional
  public void assign(User admin, String code, Long teacherId) {
    requireAdmin(admin);
    Course course = requireCourse(code);
    User teacher =
        users
            .findById(teacherId)
            .orElseThrow(() -> AnalyticsRequestException.invalid("Selecciona un docente válido"));
    if (!teacher.isEnabled() || !hasRole(teacher, "TEACHER")) {
      throw AnalyticsRequestException.invalid("La cuenta debe estar activa y tener el rol Docente");
    }
    if (!assignments.existsByCourse_CodeAndTeacher_Id(code, teacherId)) {
      assignments.save(TeachingAssignment.builder().course(course).teacher(teacher).build());
    }
  }

  @Transactional
  public void revoke(User admin, String code, Long teacherId) {
    requireAdmin(admin);
    requireCourse(code);
    assignments.deleteByCourse_CodeAndTeacher_Id(code, teacherId);
  }

  private Course requireCourse(String code) {
    return courses
        .findByCode(code)
        .orElseThrow(() -> AnalyticsRequestException.invalid("Selecciona un curso válido"));
  }

  private static void requireAdmin(User actor) {
    if (!hasRole(actor, "ADMIN")) throw AnalyticsRequestException.forbidden();
  }

  private static void requireAnalyst(User actor) {
    if (!hasRole(actor, "ADMIN") && !hasRole(actor, "TEACHER"))
      throw AnalyticsRequestException.forbidden();
  }

  private static boolean hasRole(User actor, String role) {
    return actor != null
        && actor.isEnabled()
        && actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }
}
