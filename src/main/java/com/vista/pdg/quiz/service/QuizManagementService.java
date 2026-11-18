package com.vista.pdg.quiz.service;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.BankQuestionDto;
import com.vista.pdg.quiz.dto.QuizDtos.CreateQuizRequest;
import com.vista.pdg.quiz.dto.QuizDtos.GenerateBatchResponse;
import com.vista.pdg.quiz.dto.QuizDtos.ManagedCourseDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizDetailDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuizSummaryDto;
import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.quiz.entity.QuestionContent;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizAttempt;
import com.vista.pdg.quiz.entity.QuizAttemptItem;
import com.vista.pdg.quiz.entity.QuizQuestion;
import com.vista.pdg.quiz.entity.QuizStatus;
import com.vista.pdg.quiz.material.MaterialExcerpts;
import com.vista.pdg.quiz.repository.QuizAttemptItemRepository;
import com.vista.pdg.quiz.repository.QuizAttemptRepository;
import com.vista.pdg.quiz.repository.QuizMaterialRepository;
import com.vista.pdg.quiz.repository.QuizQuestionRepository;
import com.vista.pdg.quiz.repository.QuizRepository;
import com.vista.pdg.quiz.service.QuizQuestionGenerator.Batch;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private final QuizQuestionGenerator generator;
  private final QuizRepository quizzes;
  private final QuizQuestionRepository questions;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptItemRepository items;
  private final QuizMaterialRepository materials;
  private final MaterialExcerpts excerpts;
  private final Clock clock;

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
    if (selected.stream().anyMatch(key -> topics.available(key).isEmpty())) {
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
                .instructions(QuizGuidance.clean(request.instructions()))
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
    boolean draft = quiz.getStatus() == QuizStatus.DRAFT;
    List<BankQuestionDto> bank =
        questions.findByQuiz_IdOrderByIdAsc(quizId).stream()
            .map(q -> bankQuestion(q, usage.getOrDefault(q.getId(), 0), draft))
            .toList();
    return new QuizDetailDto(
        summary(quiz, bank.size()),
        bank,
        quiz.getInstructions(),
        materials.findByQuiz_IdOrderByIdAsc(quizId).stream().map(QuizMapper::material).toList());
  }

  /** HU-36: una pregunta se edita en borrador o, ya publicada, si nunca se asignó. */
  static BankQuestionDto bankQuestion(QuizQuestion q, int timesAssigned, boolean draft) {
    QuestionContent c = q.getContent();
    return new BankQuestionDto(
        q.getId(),
        c.getTopicKey(),
        c.getTopicLabel(),
        c.getScenario(),
        c.getStatement(),
        c.getOptions(),
        c.getCorrectIndex(),
        c.getExplanation(),
        c.getHint(),
        timesAssigned,
        draft || timesAssigned == 0,
        q.getEditKind(),
        q.getEditedAt());
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
    AlgorithmDescriptor topic =
        generator.leastCovered(
            quiz.getTopics(), bank.stream().map(q -> q.getContent().getTopicKey()).toList());
    long covered =
        bank.stream().filter(q -> q.getContent().getTopicKey().equals(topic.key())).count();
    int count =
        QuizQuestionGenerator.batchCount(
            quiz.bankTarget(), bank.size(), quiz.getTopics().size(), covered, batchSize);
    Batch batch = generator.generate(topic, count, guidance(quiz, topic));
    Instant now = clock.instant();
    for (QuestionContent content : batch.questions()) {
      questions.save(QuizQuestion.builder().quiz(quiz).content(content).createdAt(now).build());
    }
    int size = bank.size() + batch.questions().size();
    return new GenerateBatchResponse(
        batch.questions().size(),
        batch.discarded(),
        size,
        quiz.bankTarget(),
        size >= quiz.bankTarget());
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

  /** Instrucciones del quiz y fragmentos de su material relacionados con el tema del lote. */
  QuizGuidance guidance(Quiz quiz, AlgorithmDescriptor topic) {
    List<MaterialExcerpts.Source> sources =
        materials.findByQuiz_IdOrderByIdAsc(quiz.getId()).stream()
            .map(m -> new MaterialExcerpts.Source(m.getName(), m.getContent()))
            .toList();
    return new QuizGuidance(
        quiz.getInstructions(),
        sources.isEmpty() ? List.of() : excerpts.select(sources, topic, MaterialExcerpts.BUDGET));
  }

  QuizSummaryDto summary(Quiz quiz, int bankSize) {
    List<QuizAttempt> started = attempts.findByQuiz_Id(quiz.getId());
    return mapper.summary(
        quiz, bankSize, access.enrolled(quiz.getCourse()).size(), started, clock.instant());
  }

  Quiz requireDraft(User actor, Long quizId) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    if (quiz.getStatus() != QuizStatus.DRAFT) {
      throw QuizException.conflict(
          "QUIZ_ALREADY_PUBLISHED", "Un quiz publicado ya no admite cambios en su banco");
    }
    return quiz;
  }
}
