package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vista.pdg.academic.entity.*;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository;
import com.vista.pdg.telemetry.service.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PedagogicalAnalyticsServiceTest {
  @Test
  void invalidDifficultyConfigurationFailsAtStartup() {
    for (double value : new double[] {-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThatThrownBy(() -> new PedagogicalAnalyticsService(null, null, value))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void permissionIsCheckedBeforeReadingAnyMetrics() {
    var assignments = mock(TeachingAssignmentService.class);
    var repository = mock(PedagogicalAnalyticsRepository.class);
    var filter =
        AnalyticsFilter.parse(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));
    var actor = User.builder().id(9L).build();
    when(assignments.requireAccess(actor, "CEDI-G1"))
        .thenThrow(AnalyticsRequestException.forbidden());
    var service = new PedagogicalAnalyticsService(assignments, repository, .3);
    assertThatThrownBy(() -> service.report(actor, filter))
        .isInstanceOf(AnalyticsRequestException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void everyViewUsesTheAuthorizedCourseTermAndSameFilters() {
    var assignments = mock(TeachingAssignmentService.class);
    var repository = mock(PedagogicalAnalyticsRepository.class);
    var filter =
        AnalyticsFilter.parse(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));
    var actor = User.builder().id(9L).build();
    var course =
        Course.builder()
            .code("CEDI-G1")
            .name("CEDI")
            .term(AcademicTerm.builder().code("2026-1").build())
            .build();
    when(assignments.requireAccess(actor, "CEDI-G1")).thenReturn(course);
    var report = new PedagogicalAnalyticsService(assignments, repository, .6).report(actor, filter);
    assertThat(report.course().termCode()).isEqualTo("2026-1");
    assertThat(report.retryThreshold()).isEqualTo(.6);
    verify(repository).structures(filter, "2026-1");
    verify(repository).algorithms(filter, "2026-1");
    verify(repository).retries(filter, "2026-1");
  }
}
