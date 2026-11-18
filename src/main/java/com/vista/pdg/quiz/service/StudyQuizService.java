package com.vista.pdg.quiz.service;

import com.vista.pdg.assistant.dto.QuotaStatus;
import com.vista.pdg.assistant.service.AssistantQuotaService;
import com.vista.pdg.assistant.service.AssistantScopeService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.dto.QuizDtos.GenerateBatchResponse;
import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.CreateStudyQuizRequest;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyQuestionDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyQuizDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.StudyQuizSummaryDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.TutorReplyDto;
import com.vista.pdg.quiz.dto.StudyQuizDtos.TutorRequest;
import com.vista.pdg.quiz.entity.QuestionContent;
import com.vista.pdg.quiz.entity.StudyQuestion;
import com.vista.pdg.quiz.entity.StudyQuiz;
import com.vista.pdg.quiz.repository.StudyQuestionRepository;
import com.vista.pdg.quiz.repository.StudyQuizRepository;
import com.vista.pdg.quiz.service.QuizQuestionGenerator.Batch;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quizzes de estudio (HU-35 · CA-5 a CA-7). Reutilizan el catálogo de temas y el generador de los
 * quizzes de curso; lo propio es que el autor es el único que los ve, que no hay ventana ni tiempo
 * y que cada pregunta ofrece pista, solución y tutor. Las llamadas al modelo que el estudiante
 * dispara (lotes y tutor) consumen la cuota diaria del asistente.
 */
@Service
@RequiredArgsConstructor
public class StudyQuizService {
  private final QuizTopics topics;
  private final QuizQuestionGenerator generator;
  private final QuizTutor tutor;
  private final StudyQuizRepository quizzes;
  private final StudyQuestionRepository questions;
  private final AssistantQuotaService quota;
  private final AssistantScopeService scope;
  private final Clock clock;
  private final Random random = new SecureRandom();

  @Value("${quiz.generation.batch-size:4}")
  private int batchSize;

  @Transactional(readOnly = true)
  public List<StudyQuizSummaryDto> list(User actor) {
    requireStudent(actor);
    List<StudyQuiz> mine = quizzes.findByOwner_IdOrderByCreatedAtDesc(actor.getId());
    Map<Long, List<StudyQuestion>> byQuiz =
        questions
            .findByStudyQuiz_IdInOrderByPositionAsc(mine.stream().map(StudyQuiz::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(q -> q.getStudyQuiz().getId()));
    return mine.stream().map(q -> summary(q, byQuiz.getOrDefault(q.getId(), List.of()))).toList();
  }

  public List<TopicDto> topics(User actor) {
    requireStudent(actor);
    return topics.all();
  }

  @Transactional
  public StudyQuizSummaryDto create(User actor, CreateStudyQuizRequest request) {
    requireStudent(actor);
    List<String> selected = new ArrayList<>(new LinkedHashSet<>(request.topics()));
    List<AlgorithmDescriptor> descriptors = new ArrayList<>();
    for (String key : selected) {
      descriptors.add(
          topics
              .available(key)
              .orElseThrow(
                  () ->
                      QuizException.invalid(
                          "topics", "Uno de los temas ya no está en el catálogo de VISTA")));
    }
    String title =
        request.title() == null || request.title().isBlank()
            ? defaultTitle(descriptors)
            : request.title().strip();
    StudyQuiz quiz =
        quizzes.save(
            StudyQuiz.builder()
                .owner(actor)
                .title(title)
                .topics(selected)
                .questionCount(request.questionCount())
                .createdAt(clock.instant())
                .build());
    return summary(quiz, List.of());
  }

  private static String defaultTitle(List<AlgorithmDescriptor> descriptors) {
    String title =
        "Estudio · "
            + descriptors.stream()
                .map(AlgorithmDescriptor::label)
                .collect(Collectors.joining(" y "));
    return title.length() <= 160 ? title : title.substring(0, 159) + "…";
  }

  /**
   * Genera un lote. No es transaccional a propósito: la llamada al modelo tarda segundos y no debe
   * retener una conexión. La cuota se reserva antes de llamar al modelo, como en el chat.
   */
  public GenerateBatchResponse generateBatch(User actor, Long id) {
    StudyQuiz quiz = requireOwn(actor, id);
    List<StudyQuestion> existing = questions.findByStudyQuiz_IdOrderByPositionAsc(id);
    int target = quiz.getQuestionCount();
    if (existing.size() >= target) {
      return new GenerateBatchResponse(0, 0, existing.size(), target, true);
    }
    List<String> generatedKeys = existing.stream().map(q -> q.getContent().getTopicKey()).toList();
    AlgorithmDescriptor topic = generator.leastCovered(quiz.getTopics(), generatedKeys);
    long covered = generatedKeys.stream().filter(topic.key()::equals).count();
    int count =
        QuizQuestionGenerator.batchCount(
            target, existing.size(), quiz.getTopics().size(), covered, batchSize);
    quota.reserve(actor);
    Batch batch = generator.generate(topic, count);
    Instant now = clock.instant();
    int position = existing.size();
    try {
      for (QuestionContent content : batch.questions()) {
        questions.save(
            StudyQuestion.builder()
                .studyQuiz(quiz)
                .position(++position)
                // Las opciones se barajan una vez: el modelo tiende a repetir la posición correcta.
                .content(content.shuffled(random))
                .createdAt(now)
                .build());
      }
    } catch (DataIntegrityViolationException e) {
      throw QuizException.conflict("STUDY_BUSY", "Ya se está generando un lote de este quiz");
    }
    int size = existing.size() + batch.questions().size();
    return new GenerateBatchResponse(
        batch.questions().size(), batch.discarded(), size, target, size >= target);
  }

  @Transactional(readOnly = true)
  public StudyQuizDto detail(User actor, Long id) {
    StudyQuiz quiz = requireOwn(actor, id);
    List<StudyQuestion> list = questions.findByStudyQuiz_IdOrderByPositionAsc(id);
    return new StudyQuizDto(summary(quiz, list), list.stream().map(StudyQuizService::dto).toList());
  }

  @Transactional
  public StudyQuestionDto answer(User actor, Long id, int position, int selectedOption) {
    StudyQuestion question = requireQuestion(actor, id, position);
    if (question.resolved()) {
      throw QuizException.conflict("ALREADY_RESOLVED", "Esta pregunta ya está resuelta");
    }
    question.setSelectedOption(selectedOption);
    question.setAnsweredAt(clock.instant());
    return dto(questions.save(question));
  }

  @Transactional
  public StudyQuestionDto hint(User actor, Long id, int position) {
    StudyQuestion question = requireQuestion(actor, id, position);
    question.setHintShown(true);
    return dto(questions.save(question));
  }

  /** Muestra la solución sin responder; una pregunta ya respondida ya la muestra. */
  @Transactional
  public StudyQuestionDto solution(User actor, Long id, int position) {
    StudyQuestion question = requireQuestion(actor, id, position);
    if (question.getSelectedOption() == null) question.setSolutionShown(true);
    return dto(questions.save(question));
  }

  /** Sin transacción: la llamada al modelo no debe retener una conexión. */
  public TutorReplyDto tutor(User actor, Long id, int position, TutorRequest request) {
    StudyQuestion question = requireQuestion(actor, id, position);
    String message = request.message();
    if (message
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
        || scope.isOutOfBounds(message)) {
      throw QuizException.invalid(
          "message", "Pregunta sobre esta pregunta del quiz, su tema o cómo verlo en VISTA.");
    }
    QuotaStatus status = quota.reserve(actor);
    String reply =
        tutor.reply(
            question.getContent(),
            question.resolved(),
            question.getSelectedOption(),
            message,
            request.history());
    return new TutorReplyDto(reply, status.remaining());
  }

  @Transactional
  public void delete(User actor, Long id) {
    StudyQuiz quiz = requireOwn(actor, id);
    questions.deleteAll(questions.findByStudyQuiz_IdOrderByPositionAsc(id));
    quizzes.delete(quiz);
  }

  private static StudyQuizSummaryDto summary(StudyQuiz quiz, List<StudyQuestion> list) {
    return new StudyQuizSummaryDto(
        quiz.getId(),
        quiz.getTitle(),
        quiz.getTopics(),
        quiz.getQuestionCount(),
        list.size(),
        (int) list.stream().filter(q -> q.getSelectedOption() != null).count(),
        (int) list.stream().filter(StudyQuestion::correct).count(),
        (int) list.stream().filter(StudyQuestion::isHintShown).count(),
        (int) list.stream().filter(StudyQuestion::isSolutionShown).count(),
        quiz.getCreatedAt());
  }

  private static StudyQuestionDto dto(StudyQuestion question) {
    QuestionContent q = question.getContent();
    boolean resolved = question.resolved();
    return new StudyQuestionDto(
        question.getPosition(),
        q.getTopicLabel(),
        q.getScenario(),
        q.getStatement(),
        q.getOptions(),
        q.getSetup(),
        question.getSelectedOption(),
        question.isHintShown(),
        question.isHintShown() ? q.getHint() : null,
        question.isSolutionShown(),
        resolved ? q.getCorrectIndex() : null,
        resolved ? q.getExplanation() : null);
  }

  private static void requireStudent(User actor) {
    if (!QuizAccess.hasRole(actor, "STUDENT")) {
      throw new QuizException(
          403, "STUDY_FORBIDDEN", "Los quizzes de estudio son para estudiantes", null);
    }
  }

  /** Ajeno o inexistente responden igual: no se confirma que exista. */
  private StudyQuiz requireOwn(User actor, Long id) {
    requireStudent(actor);
    return quizzes
        .findByIdAndOwner_Id(id, actor.getId())
        .orElseThrow(
            () ->
                new QuizException(
                    404,
                    "STUDY_QUIZ_NOT_FOUND",
                    "Este quiz de estudio no existe o no es tuyo",
                    null));
  }

  private StudyQuestion requireQuestion(User actor, Long id, int position) {
    requireOwn(actor, id);
    return questions
        .findByStudyQuiz_IdAndPosition(id, position)
        .orElseThrow(() -> QuizException.invalid("position", "Esa pregunta no existe"));
  }
}
