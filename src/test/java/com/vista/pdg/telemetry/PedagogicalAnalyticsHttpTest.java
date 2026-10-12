package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.exception.GlobalExceptionHandler;
import com.vista.pdg.telemetry.controller.AnalyticsController;
import com.vista.pdg.telemetry.controller.PedagogicalAnalyticsController;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.StructureCount;
import com.vista.pdg.telemetry.service.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Contrato MVC real con servicios simulados; seguridad JWT y SQL se cubren en el IT. */
class PedagogicalAnalyticsHttpTest {
  PedagogicalAnalyticsService analytics;
  TeachingAssignmentService assignments;
  MockMvc mvc;
  final String query = "?courseCode=CEDI-G1&from=2026-04-01&to=2026-04-30";

  @BeforeEach
  void setup() {
    analytics = mock(PedagogicalAnalyticsService.class);
    assignments = mock(TeachingAssignmentService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new PedagogicalAnalyticsController(analytics, assignments),
                new AnalyticsController())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void jsonAndCsvPreserveSuppressionAndDisableCaching() throws Exception {
    var filter =
        AnalyticsFilter.parse(
            Map.of("courseCode", "CEDI-G1", "from", "2026-04-01", "to", "2026-04-30"));
    var report =
        AnalyticsDisclosure.disclose(
            new CourseDto("CEDI-G1", "CEDI", "2026-1"),
            filter,
            .3,
            List.of(new StructureCount("pila", 400, 4)),
            List.of(),
            List.of());
    when(analytics.report(null, filter)).thenReturn(report);
    mvc.perform(get("/api/analytics/report" + query))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.structures[0].status").value("SUPPRESSED"))
        .andExpect(jsonPath("$.structures[0].frequency").doesNotExist())
        .andExpect(jsonPath("$.totalRequests").doesNotExist());
    var csv =
        mvc.perform(get("/api/analytics/report.csv" + query))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"vista-analitica-2026-04-01-2026-04-30.csv\""))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv)
        .startsWith("\ufeff")
        .contains(AnalyticsDisclosure.INSUFFICIENT)
        .doesNotContain("400");
    verify(analytics, times(2)).report(null, filter);
  }

  @Test
  void duplicateIndividualAndInvalidFiltersNeverReachService() throws Exception {
    for (String path : List.of("report", "report.csv")) {
      for (String suffix :
          List.of("&courseCode=OTHER", "&from=2026-04-02", "&studentId=5", "&minParticipants=1")) {
        mvc.perform(get("/api/analytics/" + path + query + suffix))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ANALYTICS_FILTER"));
      }
      mvc.perform(get("/api/analytics/" + path + query.replace("2026-04-30", "2026-04-31")))
          .andExpect(status().isBadRequest());
      mvc.perform(get("/api/analytics/" + path)).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(analytics);
  }

  @Test
  void revokedAccessIsTranslatedTo403ForReportAndExport() throws Exception {
    when(analytics.report(isNull(), any())).thenThrow(AnalyticsRequestException.forbidden());
    for (String path : List.of("report", "report.csv")) {
      mvc.perform(get("/api/analytics/" + path + query))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ANALYTICS_FORBIDDEN"));
    }
  }

  @Test
  void authorizedCourseListIsNeverCached() throws Exception {
    when(assignments.availableCourses(null))
        .thenReturn(List.of(new CourseDto("CEDI-G1", "CEDI", "2026-1")));
    mvc.perform(get("/api/analytics/courses"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$[0].code").value("CEDI-G1"));
  }

  @Test
  void retiredRoutesCannotReturnRawTelemetry() throws Exception {
    for (String path : List.of("summary", "events", "retries")) {
      mvc.perform(get("/api/analytics/" + path))
          .andExpect(status().isGone())
          .andExpect(jsonPath("$.code").value("ANALYTICS_ENDPOINT_RETIRED"));
    }
    verifyNoInteractions(analytics, assignments);
  }
}
