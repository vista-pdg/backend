package com.vista.pdg.quiz.controller;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.AnswerRequest;
import com.vista.pdg.quiz.dto.QuizDtos.AttemptDto;
import com.vista.pdg.quiz.dto.QuizDtos.AttemptReviewDto;
import com.vista.pdg.quiz.dto.QuizDtos.IntegrityEventRequest;
import com.vista.pdg.quiz.dto.QuizDtos.IntegrityEventResponse;
import com.vista.pdg.quiz.dto.QuizDtos.StudentQuizListDto;
import com.vista.pdg.quiz.service.QuizAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Quizzes del curso del estudiante (HU-34). El curso sale de la cuenta, nunca de la petición. */
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizStudentController {
  private final QuizAttemptService attempts;

  @GetMapping
  public ResponseEntity<StudentQuizListDto> list(@AuthenticationPrincipal User actor) {
    return noStore(attempts.list(actor));
  }

  @PostMapping("/{quizId}/attempt")
  public ResponseEntity<AttemptDto> start(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return noStore(attempts.start(actor, quizId));
  }

  @GetMapping("/{quizId}/attempt")
  public ResponseEntity<AttemptDto> view(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return noStore(attempts.view(actor, quizId));
  }

  /** HU-35 · CA-4: revisión de solo lectura del intento entregado. */
  @GetMapping("/{quizId}/attempt/review")
  public ResponseEntity<AttemptReviewDto> review(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return noStore(attempts.review(actor, quizId));
  }

  @PutMapping("/{quizId}/attempt/answers/{position}")
  public ResponseEntity<AttemptDto> answer(
      @AuthenticationPrincipal User actor,
      @PathVariable Long quizId,
      @PathVariable int position,
      @Valid @RequestBody AnswerRequest request) {
    return noStore(
        attempts.answer(actor, quizId, position, request.selectedOption(), request.elapsedMs()));
  }

  @PostMapping("/{quizId}/attempt/integrity")
  public IntegrityEventResponse integrity(
      @AuthenticationPrincipal User actor,
      @PathVariable Long quizId,
      @Valid @RequestBody IntegrityEventRequest request) {
    return attempts.reportIntegrity(actor, quizId, request.type());
  }

  @PostMapping("/{quizId}/attempt/submit")
  public ResponseEntity<AttemptDto> submit(
      @AuthenticationPrincipal User actor, @PathVariable Long quizId) {
    return noStore(attempts.submit(actor, quizId));
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
  }
}
