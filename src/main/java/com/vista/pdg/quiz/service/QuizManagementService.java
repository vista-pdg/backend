package com.vista.pdg.quiz.service;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.quiz.dto.QuizDtos.BankQuestionDto;
import com.vista.pdg.quiz.dto.QuizDtos.CreateQuizRequest;
import com.vista.pdg.quiz.dto.QuizDtos.GenerateBatchResponse;
import com.vista.pdg.quiz.dto.QuizDtos.ManagedCourseDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizDetailDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizSummaryDto;
import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizAttempt;
import com.vista.pdg.quiz.entity.QuizAttemptItem;
import com.vista.pdg.quiz.entity.QuizQuestion;
import com.vista.pdg.quiz.entity.QuizStatus;
import com.vista.pdg.quiz.repository.QuizAttemptItemRepository;
import com.vista.pdg.quiz.repository.QuizAttemptRepository;
import com.vista.pdg.quiz.repository.QuizQuestionRepository;
import com.vista.pdg.quiz.repository.QuizRepository;
import com.vista.pdg.quiz.service.QuizQuestionWriter.Draft;
import com.vista.pdg.quiz.service.QuizScenarioFactory.Scenario;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida de un quiz desde el lado docente: crear, poblar el banco, revisar y publicar. */
@Service
@RequiredArgsConstructor
public class QuizManagementService {
  static final int MAX_BANK = 600;

  private final QuizAccess access;
  private final QuizTopics topics;
  private final QuizMapper mapper;
  private final QuizScenarioFactory scenarios;
  private final QuizQuestionWriter writer;
  private final QuizRepository quizzes;
  private final QuizQuestionRepository questions;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptItemRepository items;
  private final Clock clock;
  private final Random random = new SecureRandom();

  @Value("${quiz.generation.batch-size:4}")
  private int batchSize;

  @Transactional(readOnly = true)
  public List<ManagedCourseDto> courses(User actor) {
    return access.managedCourses(actor).stream()
        .map(
            c ->
                new ManagedCourseDto(
                    c.getCode(), c.getName(), c.getTerm().getCode(), access.enrolled(c).size()))
        .toList();
  }

  public List<TopicDto> topics() {
    return topics.all();
  }

  @Transactional
  public QuizSummaryDto create(User actor, CreateQuizRequest request) {
    Course course = access.requireManagedCourse(actor, request.courseCode());
    List<String> selected = new ArrayList<>(new LinkedHashSet<>(request.topics()));
    if (selected.stream().anyMatch(key -> topics.find(key).isEmpty())) {
      throw QuizException.invalid("topics", "Uno de los temas ya no está en el catálogo de VISTA");
    }
    if (!request.closesAt().isAfter(request.opensAt())) {
      throw QuizException.invalid("closesAt", "El cierre debe ser posterior a la apertura");
    }
    if (!request.closesAt().isAfter(clock.instant())) {
      throw QuizException.invalid("closesAt", "El cierre debe estar en el futuro");
    }
    int enrolled = access.enrolled(course).size();
    int students = request.studentCount() != null ? request.studentCount() : enrolled;
    if (students < 1) {
      throw QuizException.invalid(
          "studentCount", "El curso no tiene matriculados: indica el número de estudiantes");
    }
    if (request.questionsPerStudent() * students > MAX_BANK) {
      throw QuizException.invalid(
          "studentCount", "El banco no puede superar " + MAX_BANK + " preguntas");
    }
    Quiz quiz =
        quizzes.save(
            Quiz.builder()
                .course(course)
                .title(request.title().strip())
                .topics(selected)
                .questionsPerStudent(request.questionsPerStudent())
                .expectedStudents(students)
                .timeLimitMinutes(request.timeLimitMinutes())
                .opensAt(request.opensAt())
                .closesAt(request.closesAt())
                .showGrade(request.showGrade())
                .status(QuizStatus.DRAFT)
                .createdBy(actor)
                .createdAt(clock.instant())
                .build());
    return mapper.summary(quiz, 0, enrolled, List.of(), clock.instant());
  }

  @Transactional(readOnly = true)
  public List<QuizSummaryDto> list(User actor, String courseCode) {
    List<String> codes =
        courseCode == null || courseCode.isBlank()
            ? access.managedCourses(actor).stream().map(Course::getCode).toList()
            : List.of(access.requireManagedCourse(actor, courseCode).getCode());
    Map<String, Integer> enrolled = new HashMap<>();
    return quizzes.findByCourse_CodeInOrderByCreatedAtDesc(codes).stream()
        .map(
            quiz ->
                mapper.summary(
                    quiz,
                    (int) questions.countByQuiz_Id(quiz.getId()),
                    enrolled.computeIfAbsent(
                        quiz.getCourse().getCode(), c -> access.enrolled(quiz.getCourse()).size()),
                    attempts.findByQuiz_Id(quiz.getId()),
                    clock.instant()))
        .toList();
  }

  @Transactional(readOnly = true)
  public QuizDetailDto detail(User actor, Long quizId) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    Map<Long, Integer> usage = new HashMap<>();
    for (QuizAttemptItem item :
        items.findByAttempt_Quiz_IdOrderByAttempt_IdAscPositionAsc(quizId)) {
      usage.merge(item.getQuestion().getId(), 1, Integer::sum);
    }
    List<BankQuestionDto> bank =
        questions.findByQuiz_IdOrderByIdAsc(quizId).stream()
            .map(
                q ->
                    new BankQuestionDto(
                        q.getId(),
                        q.getTopicKey(),
                        q.getTopicLabel(),
                        q.getScenario(),
                        q.getStatement(),
                        q.getOptions(),
                        q.getCorrectIndex(),
                        q.getExplanation(),
                        usage.getOrDefault(q.getId(), 0)))
            .toList();
    return new QuizDetailDto(summary(quiz, bank.size()), bank);
  }

  /**
   * Genera un lote. No es transaccional a propósito: la llamada al modelo tarda segundos y no debe
   * retener una conexión; cada pregunta válida se guarda en cuanto se obtiene.
   */
  public GenerateBatchResponse generateBatch(User actor, Long quizId) {
    Quiz quiz = requireDraft(actor, quizId);
    List<QuizQuestion> bank = questions.findByQuiz_IdOrderByIdAsc(quizId);
    int missing = quiz.bankTarget() - bank.size();
    if (missing <= 0) {
      return new GenerateBatchResponse(0, 0, bank.size(), quiz.bankTarget(), true);
    }
    AlgorithmDescriptor topic = leastCovered(quiz, bank);
    // Cuota por tema: un lote no debe dejar a un tema con más de su parte del banco.
    int quota = Math.ceilDiv(quiz.bankTarget(), quiz.getTopics().size());
    long covered = bank.stream().filter(q -> q.getTopicKey().equals(topic.key())).count();
    int count =
        Math.min(Math.min(Math.max(1, batchSize), missing), (int) Math.max(1, quota - covered));
    List<Scenario> batch = new ArrayList<>();
    for (int i = 1; i <= count; i++) batch.add(scenarios.build(topic, random, i));

    List<Draft> drafts;
    try {
      drafts = writer.write(topic, batch);
    } catch (LlmUnavailableException | LlmExhaustedException e) {
      throw QuizException.unavailable(e.getMessage());
    } catch (UnsupportedOperationException e) {
      throw QuizException.unavailable("El modelo configurado no puede generar preguntas");
    }
    Instant now = clock.instant();
    for (Draft draft : drafts) {
      questions.save(
          QuizQuestion.builder()
              .quiz(quiz)
              .topicKey(topic.key())
              .topicLabel(QuizTopics.label(topic))
              .scenario(draft.scenario().description())
              .statement(draft.statement())
              .options(draft.options())
              .correctIndex(draft.correctIndex())
              .explanation(draft.explanation())
              .createdAt(now)
              .build());
    }
    int size = bank.size() + drafts.size();
    return new GenerateBatchResponse(
        drafts.size(), count - drafts.size(), size, quiz.bankTarget(), size >= quiz.bankTarget());
  }

  /** El tema con menos preguntas en el banco: reparte el banco de forma pareja entre temas. */
  private AlgorithmDescriptor leastCovered(Quiz quiz, List<QuizQuestion> bank) {
    Map<String, Long> perTopic = new HashMap<>();
    bank.forEach(q -> perTopic.merge(q.getTopicKey(), 1L, Long::sum));
    return quiz.getTopics().stream()
        .filter(key -> topics.find(key).isPresent())
        .min(Comparator.comparingLong(key -> perTopic.getOrDefault(key, 0L)))
        .flatMap(topics::find)
        .orElseThrow(
            () ->
                QuizException.invalid(
                    "topics", "Los temas de este quiz ya no están en el catálogo de VISTA"));
  }

  @Transactional
  public void deleteQuestion(User actor, Long quizId, Long questionId) {
    requireDraft(actor, quizId);
    QuizQuestion question =
        questions
            .findById(questionId)
            .filter(q -> q.getQuiz().getId().equals(quizId))
            .orElseThrow(QuizException::notFound);
    questions.delete(question);
  }

  @Transactional
  public QuizSummaryDto publish(User actor, Long quizId) {
    Quiz quiz = requireDraft(actor, quizId);
    int bank = (int) questions.countByQuiz_Id(quizId);
    if (bank < quiz.bankTarget()) {
      throw QuizException.conflict(
          "QUIZ_BANK_INCOMPLETE",
          "El banco tiene " + bank + " de " + quiz.bankTarget() + " preguntas");
    }
    if (!quiz.getClosesAt().isAfter(clock.instant())) {
      throw QuizException.conflict(
          "QUIZ_WINDOW_PASSED", "La fecha de cierre ya pasó; crea un quiz con una ventana vigente");
    }
    quiz.setStatus(QuizStatus.PUBLISHED);
    quiz.setPublishedAt(clock.instant());
    return summary(quizzes.save(quiz), bank);
  }

  @Transactional
  public QuizSummaryDto setGradeVisibility(User actor, Long quizId, boolean showGrade) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    quiz.setShowGrade(showGrade);
    return summary(quizzes.save(quiz), (int) questions.countByQuiz_Id(quizId));
  }

  @Transactional
  public void delete(User actor, Long quizId) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    if (attempts.countByQuiz_Id(quizId) > 0) {
      throw QuizException.conflict(
          "QUIZ_HAS_ATTEMPTS", "Este quiz ya tiene intentos de estudiantes y no se puede eliminar");
    }
    questions.deleteAll(questions.findByQuiz_IdOrderByIdAsc(quizId));
    quizzes.delete(quiz);
  }

  QuizSummaryDto summary(Quiz quiz, int bankSize) {
    List<QuizAttempt> started = attempts.findByQuiz_Id(quiz.getId());
    return mapper.summary(
        quiz, bankSize, access.enrolled(quiz.getCourse()).size(), started, clock.instant());
  }

  private Quiz requireDraft(User actor, Long quizId) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    if (quiz.getStatus() != QuizStatus.DRAFT) {
      throw QuizException.conflict(
          "QUIZ_ALREADY_PUBLISHED", "Un quiz publicado ya no admite cambios en su banco");
    }
    return quiz;
  }
}
