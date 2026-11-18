package com.vista.pdg.quiz.service;

import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.quiz.entity.QuestionContent;
import com.vista.pdg.quiz.service.QuizQuestionWriter.Draft;
import com.vista.pdg.quiz.service.QuizScenarioFactory.Scenario;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Genera un lote de preguntas de un tema: sortea y valida escenarios, pide al modelo la redacción y
 * descarta lo que no cumple el contrato. Lo comparten el banco de un quiz de curso (HU-34) y los
 * quizzes de estudio (HU-35); cada uno decide cuántas pedir y dónde guardarlas.
 */
@Component
@RequiredArgsConstructor
public class QuizQuestionGenerator {
  private final QuizTopics topics;
  private final QuizScenarioFactory scenarios;
  private final QuizQuestionWriter writer;
  private final Random random = new SecureRandom();

  public record Batch(List<QuestionContent> questions, int requested) {
    public int discarded() {
      return requested - questions.size();
    }
  }

  public Batch generate(AlgorithmDescriptor topic, int count) {
    List<Scenario> drawn = new ArrayList<>();
    for (int i = 1; i <= count; i++) scenarios.build(topic, random, i).ifPresent(drawn::add);
    if (drawn.isEmpty()) {
      throw QuizException.unavailable(
          "VISTA no pudo preparar un escenario reproducible de «"
              + QuizTopics.label(topic)
              + "». Intenta de nuevo.");
    }
    List<Draft> drafts;
    try {
      drafts = writer.write(topic, drawn);
    } catch (LlmUnavailableException | LlmExhaustedException e) {
      throw QuizException.unavailable(e.getMessage());
    } catch (UnsupportedOperationException e) {
      throw QuizException.unavailable("El modelo configurado no puede generar preguntas");
    }
    return new Batch(drafts.stream().map(d -> content(topic, d)).toList(), count);
  }

  /**
   * El tema con menos preguntas generadas: reparte el banco de forma pareja. Solo cuenta los temas
   * que VISTA todavía puede reproducir.
   */
  public AlgorithmDescriptor leastCovered(List<String> wanted, Collection<String> generatedKeys) {
    Map<String, Long> perTopic = new HashMap<>();
    generatedKeys.forEach(key -> perTopic.merge(key, 1L, Long::sum));
    return wanted.stream()
        .filter(key -> topics.available(key).isPresent())
        .min(Comparator.comparingLong(key -> perTopic.getOrDefault(key, 0L)))
        .flatMap(topics::available)
        .orElseThrow(
            () ->
                QuizException.invalid(
                    "topics", "Los temas de este quiz ya no están en el catálogo de VISTA"));
  }

  /**
   * Cuántas preguntas pedir en el siguiente lote: no más que el lote máximo ni que las que faltan,
   * y sin que un tema supere su parte del total.
   */
  public static int batchCount(int target, int generated, int topicCount, long covered, int max) {
    int share = Math.ceilDiv(target, Math.max(1, topicCount));
    int missing = target - generated;
    return Math.min(Math.min(Math.max(1, max), missing), (int) Math.max(1, share - covered));
  }

  static QuestionContent content(AlgorithmDescriptor topic, Draft draft) {
    String hint =
        draft.hint() == null || draft.hint().isBlank()
            ? "Reproduce el escenario en VISTA y recorre los pasos de «"
                + topic.label()
                + "» antes de elegir."
            : draft.hint();
    return QuestionContent.builder()
        .topicKey(topic.key())
        .topicLabel(QuizTopics.label(topic))
        .scenario(draft.scenario().description())
        .statement(draft.statement())
        .options(draft.options())
        .correctIndex(draft.correctIndex())
        .explanation(draft.explanation())
        .hint(hint)
        .facts(draft.scenario().facts())
        .setup(draft.scenario().setup())
        .build();
  }
}
