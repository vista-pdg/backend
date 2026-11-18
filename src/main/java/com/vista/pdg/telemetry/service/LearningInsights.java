package com.vista.pdg.telemetry.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.telemetry.dto.PedagogicalReport.Level;
import com.vista.pdg.telemetry.dto.PedagogicalReport.QuestionInsight;
import com.vista.pdg.telemetry.dto.PedagogicalReport.QuizInsight;
import com.vista.pdg.telemetry.dto.PedagogicalReport.TopicInsight;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.AttemptRow;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.QuizItemRow;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.QuizRow;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.StudyRow;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-36 · CA-6 a CA-8: agrega las respuestas de quizzes y de estudio por tema, por pregunta y por
 * quiz. Es una función pura sobre filas ya acotadas al curso y al periodo; no conoce identidades
 * más allá de cruzar a cada estudiante con su propia práctica.
 */
public final class LearningInsights {
  static final int MIN_QUESTION_ANSWERS = 3;
  static final int SMALL_SAMPLE = 5;
  static final int LIST_SIZE = 8;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LearningInsights() {}

  public record Inputs(
      List<QuizItemRow> items,
      List<AttemptRow> attempts,
      List<QuizRow> quizzes,
      List<StudyRow> study,
      Map<String, Long> practiceByTopic,
      Map<Long, Map<String, Instant>> firstPractice,
      Map<String, String> topicLabels,
      int enrolled,
      Instant now) {}

  public record Result(
      long quizAnswers,
      Double quizAccuracy,
      Double unansweredRate,
      int quizzesWithAnswers,
      long finishedAttempts,
      long studyAnswers,
      Double studyAccuracy,
      Double medianSeconds,
      Double timedShare,
      List<TopicInsight> topics,
      List<QuestionInsight> hardest,
      List<QuestionInsight> easiest,
      List<QuizInsight> quizzes) {}

  /** Conteo de aciertos y tiempos de un conjunto de respuestas asignadas. */
  private static final class Tally {
    long assigned;
    long correct;
    long unanswered;
    long answered;
    final List<Integer> times = new ArrayList<>();

    void add(QuizItemRow row) {
      assigned++;
      if (row.selected() == null) {
        unanswered++;
        return;
      }
      answered++;
      if (row.selected() == row.correctIndex()) correct++;
      if (row.timeSpentMs() != null) times.add(row.timeSpentMs());
    }

    Double accuracy() {
      return assigned == 0 ? null : (double) correct / assigned;
    }

    Double unansweredRate() {
      return assigned == 0 ? null : (double) unanswered / assigned;
    }
  }

  private static final class QuestionTally {
    final QuizItemRow first;
    final Tally tally = new Tally();
    final Map<Integer, Long> picks = new HashMap<>();

    QuestionTally(QuizItemRow first) {
      this.first = first;
    }
  }

  private static final class TopicTally {
    final String key;
    String label;
    final Tally quiz = new Tally();
    long studyAnswered;
    long studyCorrect;
    long studyResolved;
    long hints;
    long solutions;
    final Tally practiced = new Tally();
    final Tally unpracticed = new Tally();

    TopicTally(String key, String label) {
      this.key = key;
      this.label = label;
    }
  }

  public static Result compute(Inputs in) {
    Map<String, TopicTally> topics = new LinkedHashMap<>();
    Map<Long, QuestionTally> questions = new LinkedHashMap<>();
    Tally overall = new Tally();
    for (QuizItemRow row : in.items()) {
      overall.add(row);
      TopicTally topic =
          topics.computeIfAbsent(row.topicKey(), k -> new TopicTally(k, row.topicLabel()));
      topic.quiz.add(row);
      Instant first =
          in.firstPractice().getOrDefault(row.studentId(), Map.of()).get(row.topicKey());
      (first != null && first.isBefore(row.startedAt()) ? topic.practiced : topic.unpracticed)
          .add(row);
      QuestionTally question =
          questions.computeIfAbsent(row.questionId(), k -> new QuestionTally(row));
      question.tally.add(row);
      if (row.selected() != null) question.picks.merge(row.selected(), 1L, Long::sum);
    }
    long studyAnswered = 0;
    long studyCorrect = 0;
    for (StudyRow row : in.study()) {
      TopicTally topic =
          topics.computeIfAbsent(row.topicKey(), k -> new TopicTally(k, row.topicLabel()));
      topic.studyResolved++;
      if (row.hintShown()) topic.hints++;
      if (row.solutionShown() && row.selected() == null) topic.solutions++;
      if (row.selected() != null) {
        topic.studyAnswered++;
        studyAnswered++;
        if (row.selected() == row.correctIndex()) {
          topic.studyCorrect++;
          studyCorrect++;
        }
      }
    }
    in.practiceByTopic()
        .forEach(
            (key, count) ->
                topics.computeIfAbsent(
                    key, k -> new TopicTally(k, in.topicLabels().getOrDefault(k, k))));

    Map<Long, String> quizTitles =
        in.quizzes().stream().collect(Collectors.toMap(QuizRow::id, QuizRow::title, (a, b) -> a));
    List<QuestionInsight> eligible =
        questions.values().stream()
            .filter(q -> q.tally.assigned >= MIN_QUESTION_ANSWERS)
            .map(q -> question(q, quizTitles))
            .toList();
    Map<String, QuestionInsight> worstByTopic = new HashMap<>();
    for (QuestionInsight q : eligible) {
      if (q.commonWrongShare() == null) continue;
      worstByTopic.merge(
          q.topicKey(), q, (a, b) -> a.commonWrongShare() >= b.commonWrongShare() ? a : b);
    }

    List<TopicInsight> topicInsights =
        topics.values().stream()
            .map(
                t ->
                    topic(t, in.practiceByTopic().getOrDefault(t.key, 0L), worstByTopic.get(t.key)))
            .sorted(TOPIC_ORDER)
            .toList();
    List<QuestionInsight> hardest =
        eligible.stream()
            .sorted(
                Comparator.comparingDouble(QuestionInsight::accuracy)
                    .thenComparing(Comparator.comparingLong(QuestionInsight::answers).reversed()))
            .limit(LIST_SIZE)
            .toList();
    List<QuestionInsight> easiest =
        eligible.stream()
            .filter(q -> !hardest.contains(q))
            .sorted(
                Comparator.comparingDouble(QuestionInsight::accuracy)
                    .reversed()
                    .thenComparing(
                        QuestionInsight::medianSeconds,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(LIST_SIZE)
            .toList();

    long finished = in.attempts().size();
    return new Result(
        overall.assigned,
        overall.accuracy(),
        overall.unansweredRate(),
        (int) in.items().stream().map(QuizItemRow::quizId).distinct().count(),
        finished,
        studyAnswered,
        studyAnswered == 0 ? null : (double) studyCorrect / studyAnswered,
        medianSeconds(overall.times),
        overall.answered == 0 ? null : (double) overall.times.size() / overall.answered,
        topicInsights,
        hardest,
        easiest,
        quizzes(in));
  }

  /** Primero los temas evaluados, del menor al mayor acierto; luego los solo practicados. */
  private static final Comparator<TopicInsight> TOPIC_ORDER =
      Comparator.comparing((TopicInsight t) -> basis(t) == null)
          .thenComparing(LearningInsights::basis, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              Comparator.comparingLong((TopicInsight t) -> t.quizAnswers() + t.studyAnswers())
                  .reversed())
          .thenComparing(Comparator.comparingLong(TopicInsight::practiceExecutions).reversed())
          .thenComparing(TopicInsight::topicLabel);

  private static Double basis(TopicInsight t) {
    return t.quizAccuracy() != null ? t.quizAccuracy() : t.studyAccuracy();
  }

  private static TopicInsight topic(TopicTally t, long practice, QuestionInsight worst) {
    Double study = t.studyAnswered == 0 ? null : (double) t.studyCorrect / t.studyAnswered;
    Double basis = t.quiz.assigned > 0 ? t.quiz.accuracy() : study;
    long sample = t.quiz.assigned > 0 ? t.quiz.assigned : t.studyAnswered;
    Level level =
        basis == null
            ? Level.SIN_DATOS
            : basis < 0.5 ? Level.DIFICIL : basis < 0.75 ? Level.EN_PROGRESO : Level.DOMINADO;
    return new TopicInsight(
        t.key,
        t.label,
        level,
        basis != null && sample < SMALL_SAMPLE,
        t.quiz.assigned,
        t.quiz.accuracy(),
        t.quiz.unansweredRate(),
        medianSeconds(t.quiz.times),
        t.studyAnswered,
        study,
        t.studyResolved == 0 ? null : (double) t.hints / t.studyResolved,
        t.studyResolved == 0 ? null : (double) t.solutions / t.studyResolved,
        practice,
        t.practiced.accuracy(),
        t.unpracticed.accuracy(),
        worst == null ? null : worst.commonWrongOption(),
        worst == null ? null : worst.commonWrongShare());
  }

  private static QuestionInsight question(QuestionTally q, Map<Long, String> quizTitles) {
    List<String> options = options(q.first.optionsJson());
    int correct = q.first.correctIndex();
    Map.Entry<Integer, Long> wrong =
        q.picks.entrySet().stream()
            .filter(e -> e.getKey() != correct)
            .max(
                Map.Entry.<Integer, Long>comparingByValue()
                    .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
            .orElse(null);
    return new QuestionInsight(
        q.first.quizId(),
        quizTitles.get(q.first.quizId()),
        q.first.topicKey(),
        q.first.topicLabel(),
        q.first.statement(),
        q.tally.assigned,
        q.tally.accuracy(),
        q.tally.unansweredRate(),
        medianSeconds(q.tally.times),
        option(options, correct),
        wrong == null ? null : option(options, wrong.getKey()),
        wrong == null ? null : (double) wrong.getValue() / q.tally.assigned);
  }

  private static List<QuizInsight> quizzes(Inputs in) {
    Map<Long, List<QuizItemRow>> itemsByQuiz =
        in.items().stream().collect(Collectors.groupingBy(QuizItemRow::quizId));
    Map<Long, List<AttemptRow>> attemptsByQuiz =
        in.attempts().stream().collect(Collectors.groupingBy(AttemptRow::quizId));
    List<QuizInsight> result = new ArrayList<>();
    for (QuizRow quiz : in.quizzes()) {
      Tally tally = new Tally();
      Map<String, Tally> byTopic = new LinkedHashMap<>();
      Map<String, String> labels = new HashMap<>();
      for (QuizItemRow row : itemsByQuiz.getOrDefault(quiz.id(), List.of())) {
        tally.add(row);
        byTopic.computeIfAbsent(row.topicKey(), k -> new Tally()).add(row);
        labels.putIfAbsent(row.topicKey(), row.topicLabel());
      }
      List<AttemptRow> attempts = attemptsByQuiz.getOrDefault(quiz.id(), List.of());
      List<Double> grades =
          attempts.stream().map(a -> grade(a.score(), a.total())).filter(Objects::nonNull).toList();
      List<Map.Entry<String, Tally>> ranked =
          byTopic.entrySet().stream()
              .filter(e -> e.getValue().assigned >= MIN_QUESTION_ANSWERS)
              .sorted(Comparator.comparing(e -> e.getValue().accuracy()))
              .toList();
      String weakest = ranked.isEmpty() ? null : labels.get(ranked.get(0).getKey());
      String strongest =
          ranked.size() < 2 ? null : labels.get(ranked.get(ranked.size() - 1).getKey());
      result.add(
          new QuizInsight(
              quiz.id(),
              quiz.title(),
              phase(quiz, in.now()),
              attempts.size(),
              in.enrolled(),
              grades.isEmpty()
                  ? null
                  : Math.round(
                          grades.stream().mapToDouble(Double::doubleValue).average().orElse(0)
                              * 10.0)
                      / 10.0,
              tally.accuracy(),
              medianSeconds(tally.times),
              weakest,
              strongest));
    }
    return result;
  }

  static String phase(QuizRow quiz, Instant now) {
    if (now.isBefore(quiz.opensAt())) return "SCHEDULED";
    return now.isBefore(quiz.closesAt()) ? "OPEN" : "CLOSED";
  }

  /** Escala 0,0–5,0 con un decimal, igual que la nota del quiz. */
  static Double grade(Integer score, int total) {
    if (score == null || total <= 0) return null;
    return Math.round(score * 5.0 * 10.0 / total) / 10.0;
  }

  static Double medianSeconds(List<Integer> millis) {
    if (millis.isEmpty()) return null;
    List<Integer> sorted = millis.stream().sorted().toList();
    int n = sorted.size();
    double median =
        n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    return Math.round(median / 100.0) / 10.0;
  }

  private static List<String> options(String json) {
    try {
      return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<>() {});
    } catch (IOException e) {
      return List.of();
    }
  }

  private static String option(List<String> options, int index) {
    return index >= 0 && index < options.size() ? options.get(index) : null;
  }

  /** Etiquetas por clave a partir de cualquier colección (útil para el catálogo). */
  public static <T> Map<String, String> labels(
      List<T> items, Function<T, String> key, Function<T, String> label) {
    Map<String, String> map = new HashMap<>();
    items.forEach(i -> map.putIfAbsent(key.apply(i), label.apply(i)));
    return map;
  }
}
