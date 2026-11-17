package com.vista.pdg.quiz.service;

import com.vista.pdg.quiz.dto.QuizDtos.AttemptResultDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizSummaryDto;
import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizAttempt;
import com.vista.pdg.quiz.entity.QuizStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Entidad → DTO. Las reglas de fase y de nota viven aquí para que todas las vistas coincidan. */
@Component
@RequiredArgsConstructor
public class QuizMapper {
  static final double MAX_GRADE = 5.0;

  private final QuizTopics topics;

  public QuizSummaryDto summary(
      Quiz quiz, int bankSize, int enrolled, List<QuizAttempt> attempts, Instant now) {
    return new QuizSummaryDto(
        quiz.getId(),
        quiz.getCourse().getCode(),
        quiz.getTitle(),
        quiz.getStatus().name(),
        phase(quiz, now),
        topics(quiz),
        quiz.getQuestionsPerStudent(),
        quiz.getExpectedStudents(),
        bankSize,
        quiz.bankTarget(),
        quiz.getTimeLimitMinutes(),
        quiz.getOpensAt(),
        quiz.getClosesAt(),
        quiz.isShowGrade(),
        enrolled,
        attempts.size(),
        (int) attempts.stream().filter(QuizAttempt::finished).count());
  }

  public List<TopicDto> topics(Quiz quiz) {
    return quiz.getTopics().stream()
        .map(key -> topics.find(key).map(QuizTopics::toDto).orElse(retired(key)))
        .toList();
  }

  /** Un algoritmo retirado del catálogo no debe romper la lectura de un quiz ya creado. */
  private static TopicDto retired(String key) {
    return new TopicDto(key, key, "", "", "");
  }

  public static String phase(Quiz quiz, Instant now) {
    if (quiz.getStatus() == QuizStatus.DRAFT) return "DRAFT";
    if (now.isBefore(quiz.getOpensAt())) return "SCHEDULED";
    return now.isBefore(quiz.getClosesAt()) ? "OPEN" : "CLOSED";
  }

  /** Escala 0,0–5,0 con un decimal. */
  public static Double grade(Integer score, int total) {
    if (score == null || total <= 0) return null;
    return Math.round(score * MAX_GRADE * 10.0 / total) / 10.0;
  }

  public static AttemptResultDto result(QuizAttempt attempt) {
    if (!attempt.finished()) return null;
    boolean visible = attempt.getQuiz().isShowGrade();
    return new AttemptResultDto(
        attempt.getStatus().name(),
        attempt.getFinishedAt(),
        visible,
        visible ? attempt.getScore() : null,
        visible ? attempt.getTotal() : null,
        visible ? grade(attempt.getScore(), attempt.getTotal()) : null);
  }
}
