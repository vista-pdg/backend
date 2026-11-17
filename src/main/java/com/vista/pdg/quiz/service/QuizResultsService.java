package com.vista.pdg.quiz.service;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.QuizResultsDto;
import com.vista.pdg.quiz.dto.QuizDtos.ResultStats;
import com.vista.pdg.quiz.dto.QuizDtos.StudentResultRow;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizAttempt;
import com.vista.pdg.quiz.entity.QuizAttemptItem;
import com.vista.pdg.quiz.repository.QuizAttemptItemRepository;
import com.vista.pdg.quiz.repository.QuizAttemptRepository;
import com.vista.pdg.quiz.repository.QuizQuestionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resultados individuales de un quiz, solo para quien gestiona el curso. */
@Service
@RequiredArgsConstructor
public class QuizResultsService {
  private final QuizAccess access;
  private final QuizManagementService management;
  private final QuizAttemptService attemptService;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptItemRepository items;
  private final QuizQuestionRepository questions;

  @Transactional
  public QuizResultsDto results(User actor, Long quizId) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    attemptService.closeOverdue(quizId);

    Map<Long, List<QuizAttemptItem>> itemsByAttempt = new HashMap<>();
    for (QuizAttemptItem item :
        items.findByAttempt_Quiz_IdOrderByAttempt_IdAscPositionAsc(quizId)) {
      itemsByAttempt.computeIfAbsent(item.getAttempt().getId(), k -> new ArrayList<>()).add(item);
    }
    Map<Long, QuizAttempt> byStudent = new LinkedHashMap<>();
    attempts.findByQuiz_Id(quizId).forEach(a -> byStudent.put(a.getStudent().getId(), a));

    List<StudentResultRow> rows = new ArrayList<>();
    List<User> enrolled = access.enrolled(quiz.getCourse());
    for (User student : enrolled) {
      QuizAttempt attempt = byStudent.remove(student.getId());
      rows.add(
          attempt == null
              ? notStarted(student)
              : row(attempt, itemsByAttempt.getOrDefault(attempt.getId(), List.of())));
    }
    // Quien presentó y luego cambió de curso conserva su resultado en este quiz.
    byStudent.values().stream()
        .map(a -> row(a, itemsByAttempt.getOrDefault(a.getId(), List.of())))
        .forEach(rows::add);
    rows.sort(Comparator.comparing(StudentResultRow::displayName, String.CASE_INSENSITIVE_ORDER));

    DoubleSummaryStatistics grades =
        rows.stream()
            .map(StudentResultRow::grade)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .summaryStatistics();
    boolean any = grades.getCount() > 0;
    ResultStats stats =
        new ResultStats(
            any ? Math.round(grades.getAverage() * 10.0) / 10.0 : null,
            any ? grades.getMax() : null,
            any ? grades.getMin() : null,
            (int) grades.getCount(),
            (int) rows.stream().filter(r -> r.status().equals("IN_PROGRESS")).count(),
            (int) rows.stream().filter(r -> r.status().equals("NOT_STARTED")).count(),
            enrolled.size());
    return new QuizResultsDto(
        management.summary(quiz, (int) questions.countByQuiz_Id(quizId)), stats, rows);
  }

  private static StudentResultRow notStarted(User student) {
    return new StudentResultRow(
        student.getId(),
        student.getDisplayName(),
        student.getEmail(),
        "NOT_STARTED",
        null,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  private static StudentResultRow row(QuizAttempt attempt, List<QuizAttemptItem> answered) {
    boolean finished = attempt.finished();
    return new StudentResultRow(
        attempt.getStudent().getId(),
        attempt.getStudent().getDisplayName(),
        attempt.getStudent().getEmail(),
        attempt.getStatus().name(),
        finished ? attempt.getScore() : null,
        attempt.getTotal(),
        finished ? QuizMapper.grade(attempt.getScore(), attempt.getTotal()) : null,
        attempt.getIntegrityEvents(),
        attempt.getStartedAt(),
        attempt.getFinishedAt(),
        // Mientras el intento sigue abierto no se adelanta al docente qué va bien o mal.
        finished
            ? answered.stream()
                .map(
                    i ->
                        i.getSelectedOption() == null
                            ? "UNANSWERED"
                            : i.correct() ? "CORRECT" : "INCORRECT")
                .toList()
            : List.of());
  }
}
