package com.vista.pdg.quiz.service;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.quiz.dto.QuizDtos.AiProposalDto;
import com.vista.pdg.quiz.dto.QuizDtos.BankQuestionDto;
import com.vista.pdg.quiz.dto.QuizDtos.QuestionEditRequest;
import com.vista.pdg.quiz.entity.QuestionContent;
import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizQuestion;
import com.vista.pdg.quiz.entity.QuizStatus;
import com.vista.pdg.quiz.repository.QuizAttemptItemRepository;
import com.vista.pdg.quiz.repository.QuizQuestionRepository;
import com.vista.pdg.quiz.service.QuizQuestionWriter.Draft;
import com.vista.pdg.quiz.service.QuizScenarioFactory.Scenario;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-36 · CA-3 y CA-4: edición de preguntas del banco, a mano o con una propuesta de la IA. El
 * escenario y sus hechos no cambian: son los que VISTA validó.
 */
@Service
@RequiredArgsConstructor
public class QuizBankEditor {
  static final String MANUAL = "MANUAL";
  static final String AI = "AI";

  private final QuizAccess access;
  private final QuizManagementService management;
  private final QuizQuestionRepository questions;
  private final QuizAttemptItemRepository items;
  private final QuizTopics topics;
  private final QuizQuestionWriter writer;
  private final Clock clock;

  @Transactional
  public BankQuestionDto update(
      User actor, Long quizId, Long questionId, QuestionEditRequest request) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    QuizQuestion question = requireEditable(quiz, questionId);
    QuestionRules.Edited edited = QuestionRules.validate(request);
    QuestionContent content =
        question.getContent().toBuilder()
            .statement(edited.statement())
            .options(edited.options())
            .correctIndex(edited.correctIndex())
            .explanation(edited.explanation())
            .hint(edited.hint())
            .build();
    question.setContent(content);
    question.setEditKind(AI.equals(request.source()) ? AI : MANUAL);
    question.setEditedAt(clock.instant());
    return QuizManagementService.bankQuestion(
        questions.save(question), 0, quiz.getStatus() == QuizStatus.DRAFT);
  }

  /** No es transaccional: la llamada al modelo tarda segundos y no debe retener una conexión. */
  public AiProposalDto propose(User actor, Long quizId, Long questionId, String instruction) {
    Quiz quiz = access.requireManagedQuiz(actor, quizId);
    QuizQuestion question = requireEditable(quiz, questionId);
    QuestionContent c = question.getContent();
    AlgorithmDescriptor topic =
        topics
            .find(c.getTopicKey())
            .orElseThrow(
                () ->
                    QuizException.conflict(
                        "TOPIC_UNAVAILABLE",
                        "El tema de esta pregunta ya no está en el catálogo de VISTA"));
    Scenario scenario =
        new Scenario(
            1, c.getScenario(), c.getFacts() == null ? List.of() : c.getFacts(), c.getSetup());
    Draft current =
        new Draft(
            scenario,
            c.getStatement(),
            c.getOptions(),
            c.getCorrectIndex(),
            c.getExplanation(),
            c.getHint());
    Draft proposal;
    try {
      proposal =
          writer
              .revise(topic, scenario, current, instruction, management.guidance(quiz, topic))
              .orElseThrow(
                  () ->
                      new QuizException(
                          422,
                          "AI_PROPOSAL_INVALID",
                          "La IA no propuso una versión válida. Ajusta la instrucción e inténtalo"
                              + " de nuevo.",
                          null));
    } catch (LlmUnavailableException | LlmExhaustedException e) {
      throw QuizException.unavailable(e.getMessage());
    } catch (UnsupportedOperationException e) {
      throw QuizException.unavailable("El modelo configurado no puede editar preguntas");
    }
    return new AiProposalDto(
        proposal.statement(),
        proposal.options(),
        proposal.correctIndex(),
        proposal.explanation(),
        proposal.hint());
  }

  private QuizQuestion requireEditable(Quiz quiz, Long questionId) {
    QuizQuestion question =
        questions
            .findById(questionId)
            .filter(q -> q.getQuiz().getId().equals(quiz.getId()))
            .orElseThrow(QuizException::notFound);
    if (quiz.getStatus() != QuizStatus.DRAFT && items.countByQuestion_Id(questionId) > 0) {
      throw QuizException.conflict(
          "QUESTION_LOCKED",
          "Esta pregunta ya se asignó a intentos: editarla cambiaría lo que respondieron");
    }
    return question;
  }
}
