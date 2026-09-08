package com.vista.pdg.assistant.controller;

import com.vista.pdg.assistant.dto.CourseQuotaDto;
import com.vista.pdg.assistant.dto.QuotaChangeDto;
import com.vista.pdg.assistant.dto.UpdateQuotaRequest;
import com.vista.pdg.assistant.service.CourseQuotaService;
import com.vista.pdg.auth.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Bajo {@code /api/admin/**}: sólo ADMIN, por {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class CourseAdminController {

  private final CourseQuotaService courseQuotaService;

  @GetMapping
  public List<CourseQuotaDto> list() {
    return courseQuotaService.listCourses();
  }

  @PutMapping("/{code}/quota")
  public CourseQuotaDto updateQuota(
      @PathVariable String code,
      @Valid @RequestBody UpdateQuotaRequest req,
      @AuthenticationPrincipal User admin) {
    return courseQuotaService.updateQuota(code, req.dailyQuota(), admin.getEmail());
  }

  @GetMapping("/{code}/quota-history")
  public List<QuotaChangeDto> history(@PathVariable String code) {
    return courseQuotaService.history(code);
  }
}
