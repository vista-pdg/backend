package com.vista.pdg.academic.controller;

import com.vista.pdg.academic.dto.AssignStudentCourseRequest;
import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.dto.CreateCourseRequest;
import com.vista.pdg.academic.dto.StudentEnrollmentDto;
import com.vista.pdg.academic.dto.TermDto;
import com.vista.pdg.academic.service.CourseManagementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Rutas administrativas protegidas por SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CourseManagementController {
  private final CourseManagementService management;

  @GetMapping("/terms")
  public List<TermDto> terms() {
    return management.terms();
  }

  @PostMapping("/courses")
  @ResponseStatus(HttpStatus.CREATED)
  public CourseDto createCourse(@Valid @RequestBody CreateCourseRequest request) {
    return management.create(request);
  }

  @GetMapping("/students")
  public List<StudentEnrollmentDto> students() {
    return management.students();
  }

  @GetMapping("/courses/{code}/students")
  public List<StudentEnrollmentDto> studentsIn(@PathVariable String code) {
    return management.studentsIn(code);
  }

  @PutMapping("/students/{studentId}/course")
  public StudentEnrollmentDto assign(
      @PathVariable Long studentId, @Valid @RequestBody AssignStudentCourseRequest request) {
    return management.assign(studentId, request.courseCode());
  }

  @DeleteMapping("/students/{studentId}/course")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable Long studentId) {
    management.revoke(studentId);
  }
}
