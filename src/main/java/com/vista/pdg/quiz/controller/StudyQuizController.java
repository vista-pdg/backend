package com.vista.pdg.quiz.controller;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.GenerateBatchResponse;
import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.CreateStudyQuizRequest;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyAnswerRequest;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyQuestionDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyQuizDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyQuizSummaryDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.TutorReplyDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.TutorRequest;
import com.vista.pdg.quiz.service.StudyQuizService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Quizzes de estudio del estudiante (HU-35). El autor sale de la sesión, nunca de la petición. */
@RestController
@RequestMapping("/api/study-quizzes")
@RequiredArgsConstructor
public class StudyQuizController {
  private final StudyQuizService study;

  @GetMapping
  public ResponseEntity<List<StudyQuizSummaryDto>> list(@AuthenticationPrincipal User actor) {
    return noStore(study.list(actor));
  }

  @GetMapping("/topics")
  public List<TopicDto> topics(@AuthenticationPrincipal User actor) {
    return study.topics(actor);
  }

  @PostMapping
  public ResponseEntity<StudyQuizSummaryDto> create(
      @AuthenticationPrincipal User actor, @Valid @RequestBody CreateStudyQuizRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(study.create(actor, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<StudyQuizDto> detail(
      @AuthenticationPrincipal User actor, @PathVariable Long id) {
    return noStore(study.detail(actor, id));
  }

  @PostMapping("/{id}/generate")
  public GenerateBatchResponse generate(
      @AuthenticationPrincipal User actor, @PathVariable Long id) {
    return study.generateBatch(actor, id);
  }

  @PutMapping("/{id}/questions/{position}/answer")
  public StudyQuestionDto answer(
      @AuthenticationPrincipal User actor,
      @PathVariable Long id,
      @PathVariable int position,
      @Valid @RequestBody StudyAnswerRequest request) {
    return study.answer(actor, id, position, request.selectedOption(), request.elapsedMs());
  }

  @PostMapping("/{id}/questions/{position}/hint")
  public StudyQuestionDto hint(
      @AuthenticationPrincipal User actor, @PathVariable Long id, @PathVariable int position) {
    return study.hint(actor, id, position);
  }

  @PostMapping("/{id}/questions/{position}/solution")
  public StudyQuestionDto solution(
      @AuthenticationPrincipal User actor, @PathVariable Long id, @PathVariable int position) {
    return study.solution(actor, id, position);
  }

  @PostMapping("/{id}/questions/{position}/tutor")
  public TutorReplyDto tutor(
      @AuthenticationPrincipal User actor,
      @PathVariable Long id,
      @PathVariable int position,
      @Valid @RequestBody TutorRequest request) {
    return study.tutor(actor, id, position, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal User actor, @PathVariable Long id) {
    study.delete(actor, id);
    return ResponseEntity.noContent().build();
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
  }
}
