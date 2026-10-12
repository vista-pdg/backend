package com.vista.pdg.academic.service;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.dto.CreateCourseRequest;
import com.vista.pdg.academic.dto.StudentEnrollmentDto;
import com.vista.pdg.academic.dto.TermDto;
import com.vista.pdg.academic.entity.AcademicTerm;
import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.repository.AcademicTermRepository;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración explícita de grupos y matrícula vigente; no reescribe eventos históricos. */
@Service
@RequiredArgsConstructor
public class CourseManagementService {
  private final CourseRepository courses;
  private final AcademicTermRepository terms;
  private final UserRepository users;

  @Transactional(readOnly = true)
  public List<TermDto> terms() {
    return terms.findAll(Sort.by("code")).stream().map(TermDto::from).toList();
  }

  @Transactional
  public CourseDto create(CreateCourseRequest request) {
    String code = request.code().strip().toUpperCase(Locale.ROOT);
    String name = request.name().strip();
    String termCode = request.termCode().strip();
    if (!code.matches("[A-Z0-9][A-Z0-9-]{1,31}")) {
      throw CourseManagementException.invalid(
          "El código debe tener entre 2 y 32 caracteres: letras, números o guiones");
    }
    if (name.length() < 2 || name.length() > 160) {
      throw CourseManagementException.invalid("El nombre debe tener entre 2 y 160 caracteres");
    }
    AcademicTerm term =
        terms
            .findByCode(termCode)
            .orElseThrow(
                () -> CourseManagementException.invalid("Selecciona un periodo existente"));
    if (courses.existsByCode(code)) {
      throw CourseManagementException.conflict("Ya existe un curso con ese código");
    }
    try {
      return CourseDto.from(
          courses.saveAndFlush(Course.builder().code(code).name(name).term(term).build()));
    } catch (DataIntegrityViolationException ex) {
      throw CourseManagementException.conflict("Ya existe un curso con ese código");
    }
  }

  @Transactional(readOnly = true)
  public List<StudentEnrollmentDto> students() {
    return users.findAll(Sort.by("displayName", "id")).stream()
        .filter(CourseManagementService::isActiveStudent)
        .map(StudentEnrollmentDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<StudentEnrollmentDto> studentsIn(String code) {
    requireCourse(code);
    return students().stream().filter(s -> code.equals(s.courseCode())).toList();
  }

  @Transactional
  public StudentEnrollmentDto assign(Long studentId, String code) {
    User student = requireStudent(studentId);
    Course course = requireCourse(code);
    if (!course.getTerm().isActive()) {
      throw CourseManagementException.invalid("El curso no pertenece al periodo activo");
    }
    student.setCourse(course);
    return StudentEnrollmentDto.from(users.save(student));
  }

  @Transactional
  public void revoke(Long studentId) {
    User student = requireStudent(studentId);
    student.setCourse(null);
    users.save(student);
  }

  private Course requireCourse(String code) {
    return courses
        .findByCode(code)
        .orElseThrow(() -> CourseManagementException.notFound("El curso no existe"));
  }

  private User requireStudent(Long id) {
    User student =
        users
            .findById(id)
            .orElseThrow(() -> CourseManagementException.notFound("El estudiante no existe"));
    if (!isActiveStudent(student)) {
      throw CourseManagementException.invalid(
          "La cuenta debe estar activa y tener el rol Estudiante");
    }
    return student;
  }

  private static boolean isActiveStudent(User user) {
    return user.isEnabled()
        && user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
  }
}
