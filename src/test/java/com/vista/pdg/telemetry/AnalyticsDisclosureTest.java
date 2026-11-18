package com.vista.pdg.telemetry;

import static com.vista.pdg.telemetry.dto.PedagogicalReport.Status.*;
import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.*;
import com.vista.pdg.telemetry.service.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HU-36 · CA-5: el mínimo de cinco estudiantes se exige al grupo, no a cada fila. */
class AnalyticsDisclosureTest {
  private final CourseDto course = new CourseDto("CEDI-G1", "CEDI", "2026-1");
  private final AnalyticsFilter filter =
      AnalyticsFilter.parse(
          Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));

  static LearningInsights.Result noLearning() {
    return LearningInsights.compute(
        new LearningInsights.Inputs(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            30,
            Instant.parse("2026-04-15T00:00:00Z")));
  }

  static PedagogicalReport disclose(
      CourseDto course,
      AnalyticsFilter filter,
      double threshold,
      int active,
      List<StructureCount> structures,
      List<AlgorithmCount> algorithms,
      List<RetryCount> retries) {
    return AnalyticsDisclosure.disclose(
        course,
        filter,
        null,
        threshold,
        new AnalyticsDisclosure.Measured(
            active, 30, structures, algorithms, retries, noLearning()));
  }

  @Test
  void ranksAndComputesPercentagesWithFiveActiveStudents() {
    var report =
        disclose(
            course,
            filter,
            .3,
            5,
            List.of(new StructureCount("pila", 5, 5), new StructureCount("arbol_avl", 15, 5)),
            List.of(),
            List.of());
    assertThat(report.status()).isEqualTo(AVAILABLE);
    assertThat(report.activeStudents()).isEqualTo(5);
    assertThat(report.structures())
        .extracting(r -> r.structure())
        .containsExactly("arbol_avl", "pila");
    assertThat(report.structures()).extracting(r -> r.percentage()).containsExactly(75.0, 25.0);
    assertThat(report.totalRequests()).isEqualTo(20);
  }

  @Test
  void groupBelowThresholdPublishesNothing() {
    var report =
        disclose(
            course,
            filter,
            .3,
            4,
            List.of(new StructureCount("pila", 400, 4)),
            List.of(new AlgorithmCount("BFS", 12, 4, 12, 4, 4.0)),
            List.of(new RetryCount("pila", 10, 5, 4, 2, 2)));
    assertThat(report.status()).isEqualTo(SUPPRESSED);
    assertThat(report.activeStudents()).isNull();
    assertThat(report.enrolledStudents()).isNull();
    assertThat(report.totalRequests()).isNull();
    assertThat(report.overview()).isNull();
    assertThat(report.structures()).isEmpty();
    assertThat(report.algorithms()).isEmpty();
    assertThat(report.retries()).isEmpty();
    assertThat(report.topics()).isEmpty();
    assertThat(report.hardestQuestions()).isEmpty();
    assertThat(report.quizzes()).isEmpty();
  }

  @Test
  void rowsOfFewStudentsAreShownOnceTheGroupReachesTheThreshold() {
    var report =
        disclose(
            course,
            filter,
            .3,
            12,
            List.of(new StructureCount("pila", 400, 1), new StructureCount("arbol_avl", 2, 2)),
            List.of(new AlgorithmCount("BFS", 3, 1, 3, 1, 4.0)),
            List.of(new RetryCount("pila", 10, 1, 10, 1, 9)));
    assertThat(report.structures()).allSatisfy(r -> assertThat(r.status()).isEqualTo(AVAILABLE));
    assertThat(report.structures()).extracting(r -> r.frequency()).containsExactly(400L, 2L);
    assertThat(report.algorithms().getFirst().averageCompletedSteps()).isEqualTo(4.0);
    assertThat(report.retries().getFirst().retryRate()).isEqualTo(.1);
    assertThat(report.totalRequests()).isEqualTo(402);
  }

  @Test
  void historicalExecutionsDoNotInventCompletedSteps() {
    var report =
        disclose(
            course,
            filter,
            .3,
            8,
            List.of(),
            List.of(
                new AlgorithmCount("BFS", 12, 6, 10, 5, 4.0),
                new AlgorithmCount("DFS", 10, 5, 10, 5, 3.5)),
            List.of());
    assertThat(report.algorithms().getFirst().stepsStatus()).isEqualTo(UNAVAILABLE);
    assertThat(report.algorithms().getFirst().averageCompletedSteps()).isNull();
    assertThat(report.algorithms().getLast().averageCompletedSteps()).isEqualTo(3.5);
    assertThat(report.overview().algorithmExecutions()).isEqualTo(22);
  }

  @Test
  void retriesSortByRateAndHighlightStrictlyAboveThreshold() {
    var report =
        disclose(
            course,
            filter,
            .5,
            10,
            List.of(),
            List.of(),
            List.of(
                new RetryCount("pila", 10, 5, 10, 5, 5),
                new RetryCount("arbol_avl", 20, 15, 10, 5, 5)));
    assertThat(report.retries())
        .extracting(r -> r.structure())
        .containsExactly("arbol_avl", "pila");
    assertThat(report.retries()).extracting(r -> r.retryRate()).containsExactly(.75, .5);
    assertThat(report.retries()).extracting(r -> r.highlighted()).containsExactly(true, false);
  }

  @Test
  void emptyReportHasNoInventedMetrics() {
    var report = disclose(course, filter, .3, 5, List.of(), List.of(), List.of());
    assertThat(report.totalRequests()).isZero();
    assertThat(report.structures()).isEmpty();
    assertThat(report.overview().quizAccuracy()).isNull();
    assertThat(report.overview().medianSeconds()).isNull();
  }

  @Test
  void inclusiveCivilDatesUseBogotaBoundaries() {
    assertThat(filter.start()).isEqualTo(Instant.parse("2026-04-01T05:00:00Z"));
    assertThat(filter.endExclusive()).isEqualTo(Instant.parse("2026-05-01T05:00:00Z"));
    assertThat(filter.wholeTerm()).isFalse();
  }

  @Test
  void datesAndQuizAreOptional() {
    var whole = AnalyticsFilter.parse(Map.of("courseCode", "CEDI-G1"));
    assertThat(whole.wholeTerm()).isTrue();
    assertThat(whole.start()).isNull();
    assertThat(whole.endExclusive()).isNull();
    assertThat(whole.quizId()).isNull();
    var since = AnalyticsFilter.parse(Map.of("courseCode", "CEDI-G1", "from", "2026-04-01"));
    assertThat(since.from()).isEqualTo(LocalDate.parse("2026-04-01"));
    assertThat(since.endExclusive()).isNull();
    var quiz = AnalyticsFilter.parse(Map.of("courseCode", "CEDI-G1", "quizId", "42"));
    assertThat(quiz.quizId()).isEqualTo(42L);
    var blank = AnalyticsFilter.parse(Map.of("courseCode", "CEDI-G1", "from", "", "quizId", ""));
    assertThat(blank.wholeTerm()).isTrue();
    assertThat(blank.quizId()).isNull();
  }

  @Test
  void rejectsInvalidDatesQuizAndIndividualFilters() {
    for (Map<String, String> invalid :
        List.of(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-31", "to", "2026-05-01"),
            Map.of("courseCode", "CEDI-G1", "from", "2026-05-01", "to", "2026-04-30"),
            Map.of("courseCode", "CEDI-G1", "student", "x"),
            Map.of("courseCode", "CEDI-G1", "quizId", "abc"),
            Map.of("courseCode", "=SUM(A1)"),
            Map.of("from", "2026-04-01"))) {
      assertThatThrownBy(() -> AnalyticsFilter.parse(invalid))
          .isInstanceOf(AnalyticsRequestException.class);
    }
  }

  @Test
  void csvEscapesTextAndNeutralizesFormulas() {
    var report =
        disclose(
            new CourseDto("CEDI-G1", "unused", "=1+1"),
            filter,
            .3,
            6,
            List.of(new StructureCount("árbol,\"AVL\"\n", 5, 5)),
            List.of(new AlgorithmCount("@formula", 1, 1, 1, 1, 42.0)),
            List.of());
    String csv = AnalyticsCsv.render(report);
    assertThat(csv)
        .startsWith("﻿")
        .contains("\"'=1+1\"")
        .contains("\"'@formula\"")
        .contains("\"árbol,\"\"AVL\"\"\n\"")
        .contains("\"Resumen\"", "\"Estructuras\"", "\"Algoritmos\"")
        .doesNotContain("sessionId", "pseudonym");
    assertThat(AnalyticsCsv.filename(report))
        .isEqualTo("vista-analitica-CEDI-G1-2026-04-01-2026-04-30.csv");
  }

  @Test
  void suppressedCsvOnlyExplainsTheGroupNotice() {
    var report =
        disclose(
            course,
            AnalyticsFilter.parse(Map.of("courseCode", "CEDI-G1")),
            .3,
            3,
            List.of(new StructureCount("pila", 999, 3)),
            List.of(),
            List.of());
    String csv = AnalyticsCsv.render(report);
    assertThat(csv)
        .contains(AnalyticsDisclosure.INSUFFICIENT)
        .contains("Todo el periodo")
        .doesNotContain("999", "\"Estructuras\"");
    assertThat(AnalyticsCsv.filename(report)).isEqualTo("vista-analitica-CEDI-G1-2026-1.csv");
  }
}
