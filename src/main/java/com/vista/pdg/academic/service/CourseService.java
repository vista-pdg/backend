package com.vista.pdg.academic.service;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.exception.RegistrationValidationException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

  private final CourseRepository courseRepository;

  /**
   * Cursos del periodo activo: los únicos a los que un estudiante puede vincularse al registrarse.
   */
  @Transactional(readOnly = true)
  public List<CourseDto> listEnrollable() {
    return courseRepository.findByTermActiveTrueOrderByCodeAsc().stream()
        .map(CourseDto::from)
        .toList();
  }

  /**
   * Resuelve el curso elegido en el registro. Un curso de un periodo cerrado se rechaza igual que
   * uno inexistente: la cohorte a la que se vincula un registro nuevo tiene que ser la vigente.
   */
  @Transactional(readOnly = true)
  public Course requireEnrollable(String courseCode) {
    String code = courseCode == null ? "" : courseCode.strip().toUpperCase(Locale.ROOT);
    if (code.isEmpty()) {
      throw new RegistrationValidationException(
          "courseCode", "Selecciona el curso al que perteneces");
    }
    return courseRepository
        .findByCode(code)
        .filter(c -> c.getTerm().isActive())
        .orElseThrow(
            () ->
                new RegistrationValidationException(
                    "courseCode", "El curso " + code + " no está disponible en el periodo activo"));
  }
}
