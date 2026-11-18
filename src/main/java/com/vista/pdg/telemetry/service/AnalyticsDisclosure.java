package com.vista.pdg.telemetry.service;

import static com.vista.pdg.telemetry.dto.PedagogicalReport.Status.*;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.dto.PedagogicalReport.*;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.*;
import java.util.Comparator;
import java.util.List;

/**
 * Una única frontera de divulgación, compartida por JSON y CSV. HU-36 · CA-5: el mínimo de cinco
 * estudiantes del Anexo B se exige al grupo en el periodo, no a cada fila. Por debajo, el reporte
 * no publica ninguna métrica.
 */
public final class AnalyticsDisclosure {
  public static final int MINIMUM_PARTICIPANTS = 5;
  public static final String INSUFFICIENT = "Datos insuficientes para garantizar el anonimato";

  private AnalyticsDisclosure() {}

  /** Lo que el servicio ya calculó para un curso y un periodo. */
  public record Measured(
      int activeStudents,
      int enrolledStudents,
      List<StructureCount> structures,
      List<AlgorithmCount> algorithms,
      List<RetryCount> retries,
      LearningInsights.Result learning) {}

  public static PedagogicalReport disclose(
      CourseDto course, AnalyticsFilter filter, QuizRef quiz, double threshold, Measured measured) {
    if (measured.activeStudents() < MINIMUM_PARTICIPANTS) {
      return report(
          course,
          filter,
          quiz,
          threshold,
          SUPPRESSED,
          null,
          null,
          null,
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of());
    }
    long total = measured.structures().stream().mapToLong(StructureCount::frequency).sum();
    List<StructureMetric> structures =
        measured.structures().stream()
            .map(
                r ->
                    new StructureMetric(
                        r.structure(),
                        AVAILABLE,
                        r.frequency(),
                        total > 0 ? 100.0 * r.frequency() / total : null))
            .sorted(
                Comparator.comparing(StructureMetric::frequency, Comparator.reverseOrder())
                    .thenComparing(StructureMetric::structure))
            .toList();
    List<AlgorithmMetric> algorithms =
        measured.algorithms().stream()
            .map(
                r -> {
                  // Un promedio parcial se interpretaría como el de todas las ejecuciones.
                  boolean measuredAll = r.measuredExecutions() == r.executions();
                  return new AlgorithmMetric(
                      r.algorithm(),
                      AVAILABLE,
                      r.executions(),
                      measuredAll ? AVAILABLE : UNAVAILABLE,
                      measuredAll ? r.averageSteps() : null);
                })
            .sorted(
                Comparator.comparing(AlgorithmMetric::executions, Comparator.reverseOrder())
                    .thenComparing(AlgorithmMetric::algorithm))
            .toList();
    List<RetryMetric> retries =
        measured.retries().stream()
            .map(
                r -> {
                  double rate = (double) r.retries() / r.sessions();
                  return new RetryMetric(
                      r.structure(), AVAILABLE, r.sessions(), r.retries(), rate, rate > threshold);
                })
            .sorted(
                Comparator.comparing(RetryMetric::retryRate, Comparator.reverseOrder())
                    .thenComparing(RetryMetric::structure))
            .toList();
    LearningInsights.Result l = measured.learning();
    Overview overview =
        new Overview(
            total,
            algorithms.stream().mapToLong(AlgorithmMetric::executions).sum(),
            l.quizAnswers(),
            l.quizAccuracy(),
            l.unansweredRate(),
            l.quizzesWithAnswers(),
            l.finishedAttempts(),
            l.studyAnswers(),
            l.studyAccuracy(),
            l.medianSeconds(),
            l.timedShare());
    return report(
        course,
        filter,
        quiz,
        threshold,
        AVAILABLE,
        measured.activeStudents(),
        measured.enrolledStudents(),
        total,
        overview,
        l.topics(),
        l.hardest(),
        l.easiest(),
        l.quizzes(),
        structures,
        algorithms,
        retries);
  }

  private static PedagogicalReport report(
      CourseDto course,
      AnalyticsFilter filter,
      QuizRef quiz,
      double threshold,
      Status status,
      Integer active,
      Integer enrolled,
      Long total,
      Overview overview,
      List<TopicInsight> topics,
      List<QuestionInsight> hardest,
      List<QuestionInsight> easiest,
      List<QuizInsight> quizzes,
      List<StructureMetric> structures,
      List<AlgorithmMetric> algorithms,
      List<RetryMetric> retries) {
    return new PedagogicalReport(
        course,
        filter.from(),
        filter.to(),
        filter.wholeTerm(),
        quiz,
        AnalyticsFilter.ZONE.getId(),
        MINIMUM_PARTICIPANTS,
        status,
        active,
        enrolled,
        threshold,
        3,
        120,
        total,
        overview,
        topics,
        hardest,
        easiest,
        quizzes,
        structures,
        algorithms,
        retries);
  }
}
