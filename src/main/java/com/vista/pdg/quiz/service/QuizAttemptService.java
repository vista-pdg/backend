package com.vista.pdg.quiz.service;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.AttemptDto;
import com.vista.pdg.quiz.dto.QuizDtos.AttemptQuestionDto;
import com.vista.pdg.quiz.dto.QuizDtos.IntegrityEventResponse;
import com.vista.pdg.quiz.dto.QuizDtos.StudentQuizDto;
import com.vista.pdg.quiz.dto.QuizDtos.StudentQuizListDto;
import com.vista.pdg.quiz.entity.AttemptStatus;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizAttempt;
import com.vista.pdg.quiz.entity.QuizAttemptItem;
import com.vista.pdg.quiz.entity.QuizQuestion;
import com.vista.pdg.quiz.entity.QuizStatus;
import com.vista.pdg.quiz.repository.QuizAttemptItemRepository;
import com.vista.pdg.quiz.repository.QuizAttemptRepository;
import com.vista.pdg.quiz.repository.QuizQuestionRepository;
import com.vista.pdg.quiz.repository.QuizRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Intento del estudiante. Todo plazo se decide con el reloj del servidor: el temporizador del
 * navegador es solo una ayuda visual.
 */
@Service
@Slf4j
public class QuizAttemptService {
  private final QuizAccess access;
  private final QuizTopics topics;
  private final QuizRepository quizzes;
  private final QuizQuestionRepository questions;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptItemRepository items;
  private final Clock clock;
  private final TransactionTemplate transaction;
  private final Random random = new SecureRandom();

  public QuizAttemptService(
      QuizAccess access,
      QuizTopics topics,
      QuizRepository quizzes,
      QuizQuestionRepository questions,
      QuizAttemptRepository attempts,
      QuizAttemptItemRepository items,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.access = access;
    this.topics = topics;
    this.quizzes = quizzes;
    this.questions = questions;
    this.attempts = attempts;
    this.items = items;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Transactional
  public StudentQuizListDto list(User actor) {
    Course course = access.studentCourse(actor);
    if (course == null) return new StudentQuizListDto(null, null, List.of());
    List<Quiz> published =
        quizzes.findByCourse_IdAndStatusOrderByOpensAtAsc(course.getId(), QuizStatus.PUBLISHED);
    Map<Long, QuizAttempt> mine =
        attempts
            .findByStudent_IdAndQuiz_IdIn(
                actor.getId(), published.stream().map(Quiz::getId).toList())
            .stream()
            .collect(Collectors.toMap(a -> a.getQuiz().getId(), a -> a));
    Instant now = now();
    return new StudentQuizListDto(
        course.getCode(),
        course.getName(),
        published.stream().map(quiz -> listed(quiz, mine.get(quiz.getId()), now)).toList());
  }

  private StudentQuizDto listed(Quiz quiz, QuizAttempt attempt, Instant now) {
    String state;
    if (attempt != null) {
      closeIfOverdue(attempt, now);
      state = attempt.finished() ? "FINISHED" : "IN_PROGRESS";
    } else if (now.isBefore(quiz.getOpensAt())) {
      state = "UPCOMING";
    } else {
      state = now.isBefore(quiz.getClosesAt()) ? "OPEN" : "MISSED";
    }
    return new StudentQuizDto(
        quiz.getId(),
        quiz.getTitle(),
        quiz.getTopics().stream()
            .map(key -> topics.find(key).map(QuizTopics::label).orElse(key))
            .toList(),
        quiz.getQuestionsPerStudent(),
        quiz.getTimeLimitMinutes(),
        quiz.getOpensAt(),
        quiz.getClosesAt(),
        state,
        attempt != null && !attempt.finished() ? attempt.getDeadline() : null,
        attempt == null ? null : QuizMapper.result(attempt));
  }

  /**
   * Inicia el intento o devuelve el existente. Dos peticiones simultáneas chocan en la restricción
   * única de la base; la que pierde relee el intento que creó la otra.
   */
  public AttemptDto start(User actor, Long quizId) {
    try {
      return transaction.execute(status -> startInTransaction(actor, quizId));
    } catch (DataIntegrityViolationException e) {
      return transaction.execute(status -> startInTransaction(actor, quizId));
    }
  }

  private AttemptDto startInTransaction(User actor, Long quizId) {
    Quiz quiz = requireStudentQuiz(actor, quizId);
    Instant now = now();
    QuizAttempt existing = attempts.findByQuiz_IdAndStudent_Id(quizId, actor.getId()).orElse(null);
    if (existing != null) {
      closeIfOverdue(existing, now);
      return dto(existing, now);
    }
    if (now.isBefore(quiz.getOpensAt())) {
      throw QuizException.conflict("QUIZ_NOT_OPEN", "Este quiz aún no abre");
    }
    if (!now.isBefore(quiz.getClosesAt())) {
      throw QuizException.conflict("QUIZ_CLOSED", "Este quiz ya cerró");
    }
    List<QuizQuestion> assigned = assign(quiz);
    Instant limit = now.plus(Duration.ofMinutes(quiz.getTimeLimitMinutes()));
    QuizAttempt attempt =
        attempts.saveAndFlush(
            QuizAttempt.builder()
                .quiz(quiz)
                .student(actor)
                .startedAt(now)
                .deadline(limit.isBefore(quiz.getClosesAt()) ? limit : quiz.getClosesAt())
                .status(AttemptStatus.IN_PROGRESS)
                .total(assigned.size())
                .build());
    for (int i = 0; i < assigned.size(); i++) {
      List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
      Collections.shuffle(order, random);
      items.save(
          QuizAttemptItem.builder()
              .attempt(attempt)
              .question(assigned.get(i))
              .position(i + 1)
              .optionOrder(order.stream().map(String::valueOf).collect(Collectors.joining(",")))
              .build());
    }
    return dto(attempt, now);
  }

  /**
   * Sortea las preguntas priorizando las menos usadas, de modo que con un banco de {@code n ×
   * estudiantes} cada quien recibe preguntas distintas. Entre preguntas igual de usadas prefiere un
   * tema que el estudiante aún no tenga.
   */
  private List<QuizQuestion> assign(Quiz quiz) {
    List<QuizQuestion> bank = new ArrayList<>(questions.findByQuiz_IdOrderByIdAsc(quiz.getId()));
    Map<Long, Integer> usage = new HashMap<>();
    for (QuizAttemptItem item :
        items.findByAttempt_Quiz_IdOrderByAttempt_IdAscPositionAsc(quiz.getId())) {
      usage.merge(item.getQuestion().getId(), 1, Integer::sum);
    }
    Collections.shuffle(bank, random);
    int wanted = Math.min(quiz.getQuestionsPerStudent(), bank.size());
    List<QuizQuestion> chosen = new ArrayList<>();
    Set<String> seenTopics = new HashSet<>();
    while (chosen.size() < wanted) {
      QuizQuestion next =
          bank.stream()
              .min(
                  Comparator.comparingInt((QuizQuestion q) -> usage.getOrDefault(q.getId(), 0))
                      .thenComparing(q -> seenTopics.contains(q.getTopicKey())))
              .orElseThrow();
      bank.remove(next);
      chosen.add(next);
      seenTopics.add(next.getTopicKey());
    }
    return chosen;
  }

  @Transactional
  public AttemptDto view(User actor, Long quizId) {
    QuizAttempt attempt = requireAttempt(actor, quizId);
    Instant now = now();
    closeIfOverdue(attempt, now);
    return dto(attempt, now);
  }

  @Transactional(noRollbackFor = QuizException.class)
  public AttemptDto answer(User actor, Long quizId, int position, Integer selectedOption) {
    QuizAttempt attempt = requireAttempt(actor, quizId);
    Instant now = now();
    requireOpen(attempt, now);
    QuizAttemptItem item =
        items.findByAttempt_IdOrderByPositionAsc(attempt.getId()).stream()
            .filter(i -> i.getPosition() == position)
            .findFirst()
            .orElseThrow(() -> QuizException.invalid("position", "Esa pregunta no existe"));
    // El cliente habla en el orden barajado; se guarda el índice original de la pregunta.
    item.setSelectedOption(selectedOption == null ? null : item.order().get(selectedOption));
    item.setAnsweredAt(now);
    items.save(item);
    return dto(attempt, now);
  }

  @Transactional(noRollbackFor = QuizException.class)
  public IntegrityEventResponse reportIntegrity(User actor, Long quizId, String type) {
    QuizAttempt attempt = requireAttempt(actor, quizId);
    requireOpen(attempt, now());
    attempts.incrementIntegrityEvents(attempt.getId());
    log.info(
        "Quiz {}: incidente de integridad '{}' en el intento {}", quizId, type, attempt.getId());
    return new IntegrityEventResponse(attempts.integrityEvents(attempt.getId()));
  }

  @Transactional
  public AttemptDto submit(User actor, Long quizId) {
    QuizAttempt attempt = requireAttempt(actor, quizId);
    Instant now = now();
    closeIfOverdue(attempt, now);
    if (!attempt.finished()) close(attempt, AttemptStatus.SUBMITTED, now);
    return dto(attempt, now);
  }

  /** Milisegundos: es la precisión que conserva el contrato JSON y cabe en la columna. */
  private Instant now() {
    return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  /** Cierra los intentos vencidos de un quiz; lo usa la vista de resultados del docente. */
  @Transactional
  public void closeOverdue(Long quizId) {
    Instant now = now();
    attempts.findByQuiz_Id(quizId).forEach(attempt -> closeIfOverdue(attempt, now));
  }

  /** Un intento vencido no espera a que el estudiante vuelva: se califica con lo respondido. */
  private void closeIfOverdue(QuizAttempt attempt, Instant now) {
    if (!attempt.finished() && !now.isBefore(attempt.getDeadline())) {
      close(attempt, AttemptStatus.EXPIRED, attempt.getDeadline());
    }
  }

  private void requireOpen(QuizAttempt attempt, Instant now) {
    closeIfOverdue(attempt, now);
    if (attempt.finished()) {
      throw QuizException.conflict("ATTEMPT_CLOSED", "El intento ya finalizó");
    }
  }

  private void close(QuizAttempt attempt, AttemptStatus status, Instant at) {
    int score =
        (int)
            items.findByAttempt_IdOrderByPositionAsc(attempt.getId()).stream()
                .filter(QuizAttemptItem::correct)
                .count();
    attempt.setStatus(status);
    attempt.setFinishedAt(at);
    attempt.setScore(score);
    attempts.save(attempt);
  }

  private Quiz requireStudentQuiz(User actor, Long quizId) {
    Course course = access.studentCourse(actor);
    // Un quiz ajeno, en borrador o inexistente responden igual: no se confirma su existencia.
    return quizzes
        .findById(quizId)
        .filter(q -> q.getStatus() == QuizStatus.PUBLISHED)
        .filter(q -> course != null && q.getCourse().getId().equals(course.getId()))
        .orElseThrow(QuizException::notFound);
  }

  private QuizAttempt requireAttempt(User actor, Long quizId) {
    requireStudentQuiz(actor, quizId);
    return attempts
        .findByQuiz_IdAndStudent_Id(quizId, actor.getId())
        .orElseThrow(
            () ->
                new QuizException(404, "ATTEMPT_NOT_FOUND", "Aún no has iniciado este quiz", null));
  }

  private AttemptDto dto(QuizAttempt attempt, Instant now) {
    // Tras finalizar no se reenvían las preguntas: el resultado es lo único que queda por mostrar.
    List<AttemptQuestionDto> view =
        attempt.finished()
            ? List.of()
            : items.findByAttempt_IdOrderByPositionAsc(attempt.getId()).stream()
                .map(QuizAttemptService::question)
                .toList();
    return new AttemptDto(
        attempt.getQuiz().getId(),
        attempt.getQuiz().getTitle(),
        attempt.getStatus().name(),
        attempt.getStartedAt(),
        attempt.getDeadline(),
        now,
        attempt.getIntegrityEvents(),
        view,
        QuizMapper.result(attempt));
  }

  private static AttemptQuestionDto question(QuizAttemptItem item) {
    QuizQuestion q = item.getQuestion();
    List<Integer> order = item.order();
    Integer selected = item.getSelectedOption();
    return new AttemptQuestionDto(
        item.getPosition(),
        q.getTopicLabel(),
        q.getScenario(),
        q.getStatement(),
        order.stream().map(q.getOptions()::get).toList(),
        selected == null ? null : order.indexOf(selected));
  }
}
