package com.vista.pdg.academic.controller;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.service.CourseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público: el selector del formulario de registro lo consulta antes de que exista sesión. */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;

  @GetMapping
  public List<CourseDto> enrollable() {
    return courseService.listEnrollable();
  }
}
