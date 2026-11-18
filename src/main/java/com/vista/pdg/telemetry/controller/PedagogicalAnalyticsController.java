package com.vista.pdg.telemetry.controller;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.service.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class PedagogicalAnalyticsController {
  private final PedagogicalAnalyticsService analytics;
  private final TeachingAssignmentService assignments;

  @GetMapping("/courses")
  public ResponseEntity<List<CourseDto>> courses(@AuthenticationPrincipal User actor) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(assignments.availableCourses(actor));
  }

  /** HU-36: quizzes publicados del curso, para el filtro opcional. */
  @GetMapping("/quizzes")
  public ResponseEntity<List<PedagogicalReport.QuizRef>> quizzes(
      @AuthenticationPrincipal User actor, @RequestParam String courseCode) {
    if (!courseCode.matches("[A-Za-z0-9_-]{1,32}")) {
      throw AnalyticsRequestException.invalid("Selecciona un curso válido");
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(analytics.quizzes(actor, courseCode));
  }

  @GetMapping("/report")
  public ResponseEntity<PedagogicalReport> report(
      @AuthenticationPrincipal User actor, @RequestParam MultiValueMap<String, String> params) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(analytics.report(actor, filter(params)));
  }

  @GetMapping("/report.csv")
  public ResponseEntity<byte[]> csv(
      @AuthenticationPrincipal User actor, @RequestParam MultiValueMap<String, String> params) {
    AnalyticsFilter filter = filter(params);
    PedagogicalReport report = analytics.report(actor, filter);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + AnalyticsCsv.filename(report) + "\"")
        .body(AnalyticsCsv.render(report).getBytes(StandardCharsets.UTF_8));
  }

  private static AnalyticsFilter filter(MultiValueMap<String, String> params) {
    if (params.values().stream().anyMatch(values -> values.size() != 1)) {
      throw AnalyticsRequestException.invalid("Cada filtro debe indicarse una sola vez");
    }
    return AnalyticsFilter.parse(params.toSingleValueMap());
  }
}
