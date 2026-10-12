package com.vista.pdg.academic.controller;

import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.academic.service.TeachingAssignmentService.Teacher;
import com.vista.pdg.auth.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/courses/{code}/teachers")
@RequiredArgsConstructor
public class TeachingAssignmentController {
  private final TeachingAssignmentService assignments;

  @GetMapping
  public List<Teacher> list(@AuthenticationPrincipal User admin, @PathVariable String code) {
    return assignments.teachers(admin, code);
  }

  @PutMapping("/{teacherId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assign(
      @AuthenticationPrincipal User admin,
      @PathVariable String code,
      @PathVariable Long teacherId) {
    assignments.assign(admin, code, teacherId);
  }

  @DeleteMapping("/{teacherId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(
      @AuthenticationPrincipal User admin,
      @PathVariable String code,
      @PathVariable Long teacherId) {
    assignments.revoke(admin, code, teacherId);
  }
}
