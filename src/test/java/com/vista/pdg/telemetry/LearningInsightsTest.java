package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.telemetry.dto.PedagogicalReport.Level;
import com.vista.pdg.telemetry.dto.PedagogicalReport.TopicInsight;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.AttemptRow;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.QuizItemRow;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.QuizRow;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.StudyRow;
import com.vista.pdg.telemetry.service.LearningInsights;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HU-36 · CA-6 a CA-8: agregados por tema, pregunta y quiz sobre filas sintéticas. */
class LearningInsightsTest {
  static final Instant START = Instant.parse("2026-04-10T15:00:00Z");
  static final String OPTIONS = "[\"Correcta\",\"Error A\",\"Error B\",\"Error C\"]";

  static QuizItemRow item(
      long quiz, long student, long question, String topic, Integer selected, Integer ms) {
    return new QuizItemRow(
        quiz,
        student * 100 + quiz,
        student,
        START,
        question,
        topic,
        label(topic),
        "Enunciado " + question,
        OPTIONS,
        0,
        selected,
        ms);
  }

  static String label(String topic) {
    return switch (topic) {
      case "avl" -> "Árbol AVL — Inserción";
      case "pila" -> "Pila — Pop";
      default -> topic;
    };
  }

  static LearningInsights.Result compute(
      List<QuizItemRow> items, List<StudyRow> study, Map<Long, Map<String, Instant>> first) {
    return LearningInsights.compute(
        new LearningInsights.Inputs(
            items,
            List.of(
                new AttemptRow(1, 1, 2, 4),
                new AttemptRow(1, 2, 4, 4),
                new AttemptRow(1, 3, null, 4)),
            List.of(
                new QuizRow(
                    1, "Quiz 1", "PUBLISHED", START.minusSeconds(60), START.plusSeconds(3600), 4),
                new QuizRow(
                    2,
                    "Quiz 2",
                    "PUBLISHED",
                    START.plusSeconds(86400),
                    START.plusSeconds(90000),
                    2)),
            study,
            Map.of("avl", 12L, "dfs", 3L),
            first,
            Map.of("dfs", "Grafo — DFS"),
            30,
            START.plusSeconds(120)));
  }

  @Test
  void ranksTopicsFromHardestAndExplainsTheCommonMistake() {
    List<QuizItemRow> items = new ArrayList<>();
    // AVL: 1 de 5 correctas; la opción 2 es el error más elegido (3 de 5).
    items.add(item(1, 1, 10, "avl", 0, 30_000));
    items.add(item(1, 2, 10, "avl", 2, 60_000));
    items.add(item(1, 3, 10, "avl", 2, 90_000));
    items.add(item(1, 4, 10, "avl", 2, null));
    items.add(item(1, 5, 10, "avl", null, null));
    // Pila: 3 de 3 correctas y rápidas.
    items.add(item(1, 1, 20, "pila", 0, 10_000));
    items.add(item(1, 2, 20, "pila", 0, 12_000));
    items.add(item(1, 3, 20, "pila", 0, 14_000));
    var result =
        compute(
            items,
            List.of(
                new StudyRow(1, "avl", "Árbol AVL — Inserción", 1, 0, true, false, 40_000),
                new StudyRow(2, "avl", "Árbol AVL — Inserción", null, 0, true, true, null),
                new StudyRow(3, "cola", "Cola — Dequeue", 0, 0, false, false, 5_000)),
            Map.of(
                1L,
                Map.of("avl", START.minusSeconds(600)),
                2L,
                Map.of("avl", START.plusSeconds(10))));

    assertThat(result.topics())
        .extracting(TopicInsight::topicKey)
        .containsExactly("avl", "pila", "cola", "dfs");
    TopicInsight avl = result.topics().getFirst();
    assertThat(avl.level()).isEqualTo(Level.DIFICIL);
    assertThat(avl.smallSample()).isFalse();
    assertThat(avl.quizAnswers()).isEqualTo(5);
    assertThat(avl.quizAccuracy()).isEqualTo(.2);
    assertThat(avl.unansweredRate()).isEqualTo(.2);
    assertThat(avl.medianSeconds()).isEqualTo(60.0);
    assertThat(avl.studyAnswers()).isEqualTo(1);
    assertThat(avl.studyAccuracy()).isEqualTo(0.0);
    assertThat(avl.hintRate()).isEqualTo(1.0);
    assertThat(avl.solutionRate()).isEqualTo(.5);
    assertThat(avl.practiceExecutions()).isEqualTo(12);
    // Solo el estudiante 1 practicó antes de empezar; el 2 lo hizo después.
    assertThat(avl.practicedAccuracy()).isEqualTo(1.0);
    assertThat(avl.unpracticedAccuracy()).isEqualTo(0.0);
    assertThat(avl.commonMistake()).isEqualTo("Error B");
    assertThat(avl.commonMistakeShare()).isEqualTo(.6);

    TopicInsight pila = result.topics().get(1);
    assertThat(pila.level()).isEqualTo(Level.DOMINADO);
    assertThat(pila.smallSample()).isTrue();
    assertThat(pila.commonMistake()).isNull();
    assertThat(result.topics().get(2).level()).isEqualTo(Level.DOMINADO);
    assertThat(result.topics().get(3).level()).isEqualTo(Level.SIN_DATOS);
    assertThat(result.topics().get(3).topicLabel()).isEqualTo("Grafo — DFS");

    assertThat(result.hardest())
        .extracting(q -> q.statement())
        .containsExactly("Enunciado 10", "Enunciado 20");
    assertThat(result.hardest().getFirst().commonWrongOption()).isEqualTo("Error B");
    assertThat(result.hardest().getFirst().correctOption()).isEqualTo("Correcta");
    assertThat(result.hardest().getFirst().quizTitle()).isEqualTo("Quiz 1");
    assertThat(result.easiest()).isEmpty();

    assertThat(result.quizAnswers()).isEqualTo(8);
    assertThat(result.quizAccuracy()).isEqualTo(.5);
    assertThat(result.timedShare()).isEqualTo(6.0 / 7);
    assertThat(result.medianSeconds()).isEqualTo(22.0);
    assertThat(result.studyAnswers()).isEqualTo(2);
    assertThat(result.studyAccuracy()).isEqualTo(.5);
    assertThat(result.finishedAttempts()).isEqualTo(3);
  }

  @Test
  void summarizesEachQuizWithItsWeakestAndStrongestTopic() {
    List<QuizItemRow> items = new ArrayList<>();
    for (long s = 1; s <= 3; s++) {
      items.add(item(1, s, 10, "avl", s == 1 ? 0 : 1, 20_000));
      items.add(item(1, s, 20, "pila", 0, 10_000));
    }
    var result = compute(items, List.of(), Map.of());
    var quiz1 = result.quizzes().getFirst();
    assertThat(quiz1.phase()).isEqualTo("OPEN");
    assertThat(quiz1.finished()).isEqualTo(3);
    assertThat(quiz1.enrolled()).isEqualTo(30);
    assertThat(quiz1.averageGrade()).isEqualTo(3.8);
    assertThat(quiz1.accuracy()).isCloseTo(4.0 / 6, within(1e-9));
    assertThat(quiz1.medianSeconds()).isEqualTo(15.0);
    assertThat(quiz1.weakestTopic()).isEqualTo("Árbol AVL — Inserción");
    assertThat(quiz1.strongestTopic()).isEqualTo("Pila — Pop");
    var quiz2 = result.quizzes().get(1);
    assertThat(quiz2.phase()).isEqualTo("SCHEDULED");
    assertThat(quiz2.finished()).isZero();
    assertThat(quiz2.accuracy()).isNull();
    assertThat(quiz2.weakestTopic()).isNull();
    assertThat(result.easiest()).extracting(q -> q.statement()).isEmpty();
  }

  @Test
  void questionsBelowThreeAnswersStayOutOfTheRankings() {
    var result =
        compute(
            List.of(item(1, 1, 10, "avl", 1, 1_000), item(1, 2, 10, "avl", 1, 3_000)),
            List.of(),
            Map.of());
    assertThat(result.hardest()).isEmpty();
    assertThat(result.topics().getFirst().commonMistake()).isNull();
    assertThat(result.topics().getFirst().smallSample()).isTrue();
    assertThat(result.medianSeconds()).isEqualTo(2.0);
  }
}
