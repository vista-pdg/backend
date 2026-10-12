package com.vista.pdg.telemetry;

import static com.vista.pdg.telemetry.dto.PedagogicalReport.Status.*;
import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.*;
import com.vista.pdg.telemetry.service.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsDisclosureTest {
  private final CourseDto course = new CourseDto("CEDI-G1", "CEDI", "2026-1");
  private final AnalyticsFilter filter =
      AnalyticsFilter.parse(
          Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));

  @Test
  void ranksAndComputesPercentagesWithFiveParticipants() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(new StructureCount("pila", 5, 5), new StructureCount("arbol_avl", 15, 5)),
            List.of(),
            List.of());
    assertThat(report.structures())
        .extracting(r -> r.structure())
        .containsExactly("arbol_avl", "pila");
    assertThat(report.structures()).extracting(r -> r.percentage()).containsExactly(75.0, 25.0);
    assertThat(report.totalRequests()).isEqualTo(20);
  }

  @Test
  void fourParticipantsSuppressTheRowAndComplementaryDenominators() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(new StructureCount("pila", 400, 4), new StructureCount("arbol_avl", 15, 5)),
            List.of(),
            List.of());
    assertThat(report.totalRequests()).isNull();
    assertThat(report.structures()).extracting(r -> r.percentage()).containsOnlyNulls();
    assertThat(report.structures().getFirst().structure()).isEqualTo("arbol_avl");
    assertThat(report.structures().getLast().status()).isEqualTo(SUPPRESSED);
    assertThat(report.structures().getLast().frequency()).isNull();
  }

  @Test
  void suppressedRowsDoNotLeakTheirRank() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(new StructureCount("pila", 400, 1), new StructureCount("arbol_avl", 2, 2)),
            List.of(),
            List.of());
    assertThat(report.structures())
        .extracting(r -> r.structure())
        .containsExactly("arbol_avl", "pila");
    assertThat(report.structures()).extracting(r -> r.frequency()).containsOnlyNulls();
  }

  @Test
  void historicalExecutionsDoNotInventCompletedSteps() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(),
            List.of(
                new AlgorithmCount("BFS", 12, 6, 10, 5, 4.0),
                new AlgorithmCount("DFS", 10, 5, 10, 5, 3.5)),
            List.of());
    assertThat(report.algorithms().getFirst().stepsStatus()).isEqualTo(UNAVAILABLE);
    assertThat(report.algorithms().getFirst().averageCompletedSteps()).isNull();
    assertThat(report.algorithms().getLast().averageCompletedSteps()).isEqualTo(3.5);
  }

  @Test
  void algorithmsProtectBothExecutionsAndStepsContributors() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(),
            List.of(
                new AlgorithmCount("BFS", 200, 4, 200, 4, 9.0),
                new AlgorithmCount("DFS", 10, 5, 10, 4, 2.0)),
            List.of());
    assertThat(report.algorithms().getFirst().algorithm()).isEqualTo("DFS");
    assertThat(report.algorithms().getFirst().stepsStatus()).isEqualTo(SUPPRESSED);
    assertThat(report.algorithms().getLast().executions()).isNull();
    assertThat(report.algorithms()).extracting(r -> r.averageCompletedSteps()).containsOnlyNulls();
  }

  @Test
  void retriesSortByRateAndHighlightStrictlyAboveThreshold() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .5,
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
  void retryMinoritiesAndTheirComplementAreProtected() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(),
            List.of(),
            List.of(
                new RetryCount("a", 10, 1, 10, 1, 9),
                new RetryCount("b", 10, 9, 10, 9, 1),
                new RetryCount("c", 100, 0, 4, 0, 4)));
    assertThat(report.retries())
        .allSatisfy(
            r -> {
              assertThat(r.status()).isEqualTo(SUPPRESSED);
              assertThat(r.sessions()).isNull();
              assertThat(r.sessionsWithRetries()).isNull();
              assertThat(r.retryRate()).isNull();
              assertThat(r.highlighted()).isNull();
            });
  }

  @Test
  void zeroAndAllRetriesRemainMeaningfulWhenPopulationIsSufficient() {
    var report =
        AnalyticsDisclosure.disclose(
            course,
            filter,
            .3,
            List.of(),
            List.of(),
            List.of(new RetryCount("a", 5, 0, 5, 0, 5), new RetryCount("b", 5, 5, 5, 5, 0)));
    assertThat(report.retries()).extracting(r -> r.retryRate()).containsExactly(1.0, 0.0);
  }

  @Test
  void emptyReportHasNoInventedMetrics() {
    var report = AnalyticsDisclosure.disclose(course, filter, .3, List.of(), List.of(), List.of());
    assertThat(report.totalRequests()).isZero();
    assertThat(report.structures()).isEmpty();
    assertThat(report.algorithms()).isEmpty();
    assertThat(report.retries()).isEmpty();
  }

  @Test
  void inclusiveCivilDatesUseBogotaBoundaries() {
    assertThat(filter.start()).isEqualTo(Instant.parse("2026-04-01T05:00:00Z"));
    assertThat(filter.endExclusive()).isEqualTo(Instant.parse("2026-05-01T05:00:00Z"));
  }

  @Test
  void rejectsInvalidDatesAndIndividualFilters() {
    for (Map<String, String> invalid :
        List.of(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-31", "to", "2026-05-01"),
            Map.of("courseCode", "CEDI-G1", "from", "2026-05-01", "to", "2026-04-30"),
            Map.of(
                "courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30", "student", "x"),
            Map.of("courseCode", "=SUM(A1)", "from", "2026-04-01", "to", "2026-04-30"),
            Map.of("courseCode", "CEDI-G1"))) {
      assertThatThrownBy(() -> AnalyticsFilter.parse(invalid))
          .isInstanceOf(AnalyticsRequestException.class);
    }
  }

  @Test
  void csvUsesOnlyDisclosedFieldsAndEscapesText() {
    var report =
        AnalyticsDisclosure.disclose(
            new CourseDto("CEDI-G1", "unused", "=1+1"),
            filter,
            .3,
            List.of(
                new StructureCount("pila", 999, 4), new StructureCount("árbol,\"AVL\"\n", 5, 5)),
            List.of(new AlgorithmCount("@formula", 1, 1, 1, 1, 42.0)),
            List.of(new RetryCount("pila", 99, 90, 4, 4, 4)));
    String csv = AnalyticsCsv.render(report);
    assertThat(csv)
        .startsWith("\ufeff")
        .contains("Datos insuficientes para garantizar el anonimato")
        .contains("\"'=1+1\"")
        .contains("\"'@formula\"")
        .contains("\"árbol,\"\"AVL\"\"\n\"")
        .doesNotContain("999", "42.0", "99", "90", "sessionId", "pseudonym");
  }
}
