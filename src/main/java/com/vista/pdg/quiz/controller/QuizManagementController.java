package com.vista.pdg.quiz.controller;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.CreateQuizRequest;
import com.vista.pdg.quiz.dto.QuizDtos.GenerateBatchResponse;
import com.vista.pdg.quiz.dto.QuizDtos.GradeVisibilityRequest;
import com.vista.pdg.quiz.dto.QuizDtos.ManagedCourseDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizDetailDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizResultsDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizSummaryDto;
import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.quiz.service.QuizManagementService;
import com.vista.pdg.quiz.service.QuizReportWriter;
import com.vista.pdg.quiz.service.QuizResultsService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Gestión docente de quizzes (HU-34). SecurityConfig exige TEACHER o ADMIN; el curso, el servicio.
 */
@RestController
@RequestMapping("/api/quizzes/manage")
@RequiredArgsConstructor
public class QuizManagementController {
  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final QuizManagementService management;
  private final QuizResultsService results;
  private final Clock clock;

  @GetMapping("/courses")
  public List<ManagedCourseDto> courses(@AuthenticationPrincipal User actor) {
    return management.courses(actor);
  }

  @GetMapping("/topics")
  public List<TopicDto> topics() {
    return management.topics();
  }

  @GetMapping
  public List<QuizSummaryDto> list(
      @AuthenticationPrincipal User actor, @RequestParam(required = false) String courseCode) {
    return management.list(actor, courseCode);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuizSummaryDto create(
      @AuthenticationPrincipal User actor, @Valid @RequestBody CreateQuizRequest request) {
    return management.create(actor, request);
  }

  @GetMapping("/{quizId}")
  public QuizDetailDto detail(@AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return management.detail(actor, quizId);
  }

  @DeleteMapping("/{quizId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    management.delete(actor, quizId);
  }

  @PostMapping("/{quizId}/bank/generate")
  public GenerateBatchResponse generate(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return management.generateBatch(actor, quizId);
  }

  @DeleteMapping("/{quizId}/questions/{questionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteQuestion(
      @AuthenticationPrincipal User actor,
      @PathVariable Long quizId,
      @PathVariable Long questionId) {
    management.deleteQuestion(actor, quizId, questionId);
  }

  @PostMapping("/{quizId}/publish")
  public QuizSummaryDto publish(@AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return management.publish(actor, quizId);
  }

  @PutMapping("/{quizId}/grade-visibility")
  public QuizSummaryDto gradeVisibility(
      @AuthenticationPrincipal User actor,
      @PathVariable Long quizId,
      @Valid @RequestBody GradeVisibilityRequest request) {
    return management.setGradeVisibility(actor, quizId, request.showGrade());
  }

  @GetMapping("/{quizId}/results")
  public ResponseEntity<QuizResultsDto> results(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(results.results(actor, quizId));
  }

  @GetMapping("/{quizId}/results.csv")
  public ResponseEntity<byte[]> csv(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    QuizResultsDto report = results.results(actor, quizId);
    return download(
        new MediaType("text", "csv", StandardCharsets.UTF_8),
        "vista-quiz-" + quizId + "-resultados.csv",
        QuizReportWriter.csv(report, clock.getZone()));
  }

  @GetMapping("/{quizId}/results.xlsx")
  public ResponseEntity<byte[]> xlsx(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    QuizResultsDto report = results.results(actor, quizId);
    return download(
        XLSX,
        "vista-quiz-" + quizId + "-resultados.xlsx",
        QuizReportWriter.xlsx(report, clock.getZone()));
  }

  private static ResponseEntity<byte[]> download(MediaType type, String filename, byte[] body) {
    return ResponseEntity.ok()
        .contentType(type)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(body);
  }
}
