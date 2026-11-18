package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vista.pdg.academic.entity.*;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.service.QuizAccess;
import com.vista.pdg.service.algorithm.AlgorithmDispatcher;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository;
import com.vista.pdg.telemetry.service.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PedagogicalAnalyticsServiceTest {
  @Test
  void invalidDifficultyConfigurationFailsAtStartup() {
    for (double value : new double[] {-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThatThrownBy(
              () ->
                  new PedagogicalAnalyticsService(null, null, null, null, null, null, null, value))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void permissionIsCheckedBeforeReadingAnyMetrics() {
    var assignments = mock(TeachingAssignmentService.class);
    var repository = mock(PedagogicalAnalyticsRepository.class);
    var learning = mock(LearningAnalyticsRepository.class);
    var quizAccess = mock(QuizAccess.class);
    var dispatcher = mock(AlgorithmDispatcher.class);
    var pseudonymizer = new Pseudonymizer("clave-de-prueba-de-al-menos-32-caracteres");
    var clock = Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC);
    var filter =
        AnalyticsFilter.parse(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));
    var actor = User.builder().id(9L).build();
    when(assignments.requireAccess(actor, "CEDI-G1"))
        .thenThrow(AnalyticsRequestException.forbidden());
    var service =
        new PedagogicalAnalyticsService(
            assignments, repository, learning, quizAccess, dispatcher, pseudonymizer, clock, .3);
    assertThatThrownBy(() -> service.report(actor, filter))
        .isInstanceOf(AnalyticsRequestException.class);
    verifyNoInteractions(repository, learning);
  }

  @Test
  void everyViewUsesTheAuthorizedCourseTermAndSameFilters() {
    var assignments = mock(TeachingAssignmentService.class);
    var repository = mock(PedagogicalAnalyticsRepository.class);
    var learning = mock(LearningAnalyticsRepository.class);
    var quizAccess = mock(QuizAccess.class);
    var dispatcher = mock(AlgorithmDispatcher.class);
    var pseudonymizer = new Pseudonymizer("clave-de-prueba-de-al-menos-32-caracteres");
    var clock = Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC);
    var filter =
        AnalyticsFilter.parse(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));
    var actor = User.builder().id(9L).build();
    var course =
        Course.builder()
            .code("CEDI-G1")
            .name("CEDI")
            .id(3L)
            .term(AcademicTerm.builder().code("2026-1").build())
            .build();
    when(assignments.requireAccess(actor, "CEDI-G1")).thenReturn(course);
    var report =
        new PedagogicalAnalyticsService(
                assignments, repository, learning, quizAccess, dispatcher, pseudonymizer, clock, .6)
            .report(actor, filter);
    assertThat(report.course().termCode()).isEqualTo("2026-1");
    assertThat(report.retryThreshold()).isEqualTo(.6);
    verify(repository).structures(filter, "2026-1");
    verify(repository).algorithms(filter, "2026-1");
    verify(repository).retries(filter, "2026-1");
    verify(repository).activePseudonyms(filter, "2026-1");
    verify(learning).quizItems(anyLong(), eq(filter));
    verify(learning).study(anyLong(), eq(filter));
    assertThat(report.status()).isEqualTo(PedagogicalReport.Status.SUPPRESSED);
  }
}
