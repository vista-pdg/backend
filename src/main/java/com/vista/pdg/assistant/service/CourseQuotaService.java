package com.vista.pdg.assistant.service;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.assistant.dto.CourseQuotaDto;
import com.vista.pdg.assistant.dto.QuotaChangeDto;
import com.vista.pdg.assistant.entity.QuotaChange;
import com.vista.pdg.assistant.repository.QuotaChangeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ajuste de la cuota diaria por curso, con auditoría de cada cambio (CA-6). */
@Service
@RequiredArgsConstructor
public class CourseQuotaService {

  private final CourseRepository courseRepository;
  private final QuotaChangeRepository changeRepository;
  private final AssistantQuotaService quotaService;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<CourseQuotaDto> listCourses() {
    int def = quotaService.defaultDailyQuota();
    return courseRepository.findAll(org.springframework.data.domain.Sort.by("code")).stream()
        .map(c -> CourseQuotaDto.from(c, def))
        .toList();
  }

  /**
   * Cambia la cuota y deja constancia. El nuevo límite aplica de inmediato a todos los estudiantes
   * del curso porque la cuota se resuelve en cada petición a partir del curso, no se copia al
   * usuario.
   */
  @Transactional
  public CourseQuotaDto updateQuota(String courseCode, int newQuota, String adminEmail) {
    Course course = requireCourse(courseCode);
    Integer previous = course.getDailyQuota();
    course.setDailyQuota(newQuota);
    courseRepository.save(course);
    changeRepository.save(
        QuotaChange.builder()
            .course(course)
            .previousQuota(previous)
            .newQuota(newQuota)
            .changedBy(adminEmail)
            .changedAt(clock.instant())
            .build());
    return CourseQuotaDto.from(course, quotaService.defaultDailyQuota());
  }

  @Transactional(readOnly = true)
  public List<QuotaChangeDto> history(String courseCode) {
    requireCourse(courseCode);
    return changeRepository.findByCourse_CodeOrderByChangedAtDesc(normalize(courseCode)).stream()
        .map(QuotaChangeDto::from)
        .toList();
  }

  private Course requireCourse(String code) {
    return courseRepository
        .findByCode(normalize(code))
        .orElseThrow(() -> new CourseNotFoundException(normalize(code)));
  }

  private static String normalize(String code) {
    return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
  }

  /** 404 para un curso que no existe; se maneja en {@code GlobalExceptionHandler}. */
  public static class CourseNotFoundException extends RuntimeException {
    public CourseNotFoundException(String code) {
      super("No existe el curso " + code);
    }
  }
}
