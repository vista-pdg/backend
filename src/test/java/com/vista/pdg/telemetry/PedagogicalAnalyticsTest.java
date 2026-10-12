package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.academic.entity.*;
import com.vista.pdg.academic.repository.*;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** La política se prueba con SQL real y las mismas rutas que consumirá el tablero. */
class PedagogicalAnalyticsTest extends IntegrationTestSupport {
  @Autowired com.vista.pdg.telemetry.service.TelemetryService telemetry;
  @Autowired CourseRepository courses;
  @Autowired AcademicTermRepository terms;
  @Autowired TeachingAssignmentRepository assignments;
  @Autowired UserRepository users;
  @Autowired GenerationEventRepository events;

  @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
  String jwtSecret;

  Course course;
  String teacher;
  String admin;
  String student;
  final Instant inside = Instant.parse("2026-04-15T12:00:00Z");

  @BeforeEach
  void cohort() throws Exception {
    course =
        courses.save(
            Course.builder()
                .code("HU06-mixed-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Cohorte de prueba")
                .term(terms.findFirstByActiveTrue().orElseThrow())
                .build());
    assignments.save(
        TeachingAssignment.builder()
            .course(course)
            .teacher(users.findByEmail(TEACHER_EMAIL).orElseThrow())
            .build());
    teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    admin = login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    student = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
  }

  String url(String path) {
    return "/api/analytics/"
        + path
        + "?courseCode="
        + course.getCode()
        + "&from=2026-04-01&to=2026-04-30";
  }

  GenerationEvent event(int person, String structure, String session, Instant at) {
    return GenerationEvent.builder()
        .pseudonym("participant-" + person)
        .courseCode(course.getCode())
        .termCode(course.getTerm().getCode())
        .studentParticipant(true)
        .structureType(structure)
        .kind("generation")
        .sessionId(session)
        .createdAt(at)
        .build();
  }

  void generations(String structure, int participants, int attempts) {
    for (int person = 0; person < participants; person++) {
      for (int attempt = 0; attempt < attempts; attempt++)
        events.save(event(person, structure, "s-" + person, inside.plusSeconds(attempt)));
    }
  }

  JsonNode report() throws Exception {
    String body =
        mockMvc
            .perform(get(url("report")).header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain(
            "pseudonym", "sessionId", "promptText", "participant-", "email", "displayName");
    return objectMapper.readTree(body);
  }

  @Test
  void structureRankingUsesCourseTermAndInclusiveDates() throws Exception {
    generations("pila", 5, 1);
    generations("arbol_avl", 5, 3);
    events.save(event(0, "pila", "start", Instant.parse("2026-04-01T05:00:00Z")));
    events.save(event(0, "pila", "end", Instant.parse("2026-05-01T04:59:59.999999Z")));
    events.save(event(0, "pila", "out", Instant.parse("2026-05-01T05:00:00Z")));
    events.save(event(0, "pila", "before", Instant.parse("2026-04-01T04:59:59.999999Z")));
    var other = event(0, "pila", "other", inside);
    other.setCourseCode("OTHER");
    events.save(other);
    var otherTerm = event(0, "pila", "term", inside);
    otherTerm.setTermCode("OTHER");
    events.save(otherTerm);
    JsonNode report = report();
    assertThat(report.get("totalRequests").asLong()).isEqualTo(22);
    assertThat(report.at("/structures/0/structure").asText()).isEqualTo("arbol_avl");
    assertThat(report.at("/structures/0/frequency").asLong()).isEqualTo(15);
    assertThat(report.at("/structures/1/frequency").asLong()).isEqualTo(7);
    assertThat(report.at("/structures/0/percentage").asDouble())
        .isCloseTo(100.0 * 15 / 22, within(.00001));
  }

  @Test
  void teacherListsOnlyAssignedCoursesAndFiltersEachCohort() throws Exception {
    var second =
        courses.save(
            Course.builder()
                .code("HU06-second-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Segunda cohorte")
                .term(course.getTerm())
                .build());
    var foreign =
        courses.save(
            Course.builder()
                .code("HU06-foreign-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Curso ajeno")
                .term(course.getTerm())
                .build());
    assignments.save(
        TeachingAssignment.builder()
            .course(second)
            .teacher(users.findByEmail(TEACHER_EMAIL).orElseThrow())
            .build());
    generations("pila", 5, 1);
    for (int p = 0; p < 5; p++) {
      for (int attempt = 0; attempt < 2; attempt++) {
        var e = event(p, "cola", "second-" + p, inside.plusSeconds(attempt));
        e.setCourseCode(second.getCode());
        events.save(e);
      }
    }
    String listed =
        mockMvc
            .perform(get("/api/analytics/courses").header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(listed)
        .contains(course.getCode(), second.getCode())
        .doesNotContain(foreign.getCode());
    assertThat(report().get("totalRequests").asLong()).isEqualTo(5);
    mockMvc
        .perform(
            get(url("report").replace(course.getCode(), second.getCode()))
                .header("Authorization", "Bearer " + teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRequests").value(10))
        .andExpect(jsonPath("$.structures[0].structure").value("cola"));
    for (String path : List.of("report", "report.csv")) {
      mockMvc
          .perform(
              get(url(path).replace(course.getCode(), foreign.getCode()))
                  .header("Authorization", "Bearer " + teacher))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void captureChecksRoleEvenWhenTeacherRetainsStudentCourse() {
    for (String email : List.of(TEACHER_EMAIL, STUDENT_EMAIL)) {
      var actor = users.findByEmail(email).orElseThrow();
      actor.setCourse(course);
      telemetry.record(
          com.vista.pdg.telemetry.service.InteractionEvent.of(actor, "generation")
              .structureKind("pila")
              .build());
    }
    assertThat(
            events.findAll().stream()
                .filter(e -> course.getCode().equals(e.getCourseCode()))
                .map(GenerationEvent::getStudentParticipant)
                .toList())
        .containsExactlyInAnyOrder(false, true);
  }

  @Test
  void nonStudentsAndUnverifiedHistoricalRolesCannotReachTheThreshold() throws Exception {
    generations("pila", 4, 1);
    var teacherEvent = event(4, "pila", "teacher", inside);
    teacherEvent.setStudentParticipant(false);
    events.save(teacherEvent);
    var historicalEvent = event(5, "pila", "unknown-role", inside);
    historicalEvent.setStudentParticipant(null);
    events.save(historicalEvent);
    assertThat(report().at("/structures/0/status").asText()).isEqualTo("SUPPRESSED");
    events.save(event(6, "pila", "fifth-student", inside));
    assertThat(report().at("/structures/0/frequency").asLong()).isEqualTo(5);
  }

  @Test
  void onePersonCannotSatisfyThresholdByGeneratingManyEvents() throws Exception {
    generations("pila", 1, 100);
    JsonNode report = report();
    assertThat(report.at("/structures/0/status").asText()).isEqualTo("SUPPRESSED");
    assertThat(report.at("/structures/0/frequency").isNull()).isTrue();
    assertThat(report.get("totalRequests").isNull()).isTrue();
  }

  @Test
  void fifthParticipantEnablesMetricAndCsvUsesSameSuppression() throws Exception {
    generations("pila", 4, 1);
    generations("arbol_avl", 5, 1);
    String csv =
        mockMvc
            .perform(get(url("report.csv")).header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().exists("Content-Disposition"))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(csv)
        .contains("Datos insuficientes para garantizar el anonimato")
        .doesNotContain("participant-", "session_id");
    assertThat(report().at("/structures/0/percentage").isNull()).isTrue();
    events.save(event(4, "pila", "s-4", inside));
    assertThat(report().at("/structures/0/percentage").asDouble()).isEqualTo(50.0);
  }

  @Test
  void algorithmsCountExecutionsAndReportedStepsWithoutDoubleCountingCompletions()
      throws Exception {
    for (int p = 0; p < 5; p++) {
      var run = event(p, "pila", "s-" + p, inside);
      run.setKind("algorithm");
      run.setAlgorithm("BFS");
      run.setStepCount(10);
      run.setExecutionId(UUID.randomUUID().toString());
      run.setCompletedSteps(p);
      events.save(run);
      var completion = event(p, "pila", "s-" + p, inside);
      completion.setKind("algorithm_completed");
      completion.setAlgorithm("BFS");
      completion.setStepCount(10);
      events.save(completion);
    }
    JsonNode report = report();
    assertThat(report.at("/algorithms/0/executions").asLong()).isEqualTo(5);
    assertThat(report.at("/algorithms/0/averageCompletedSteps").asDouble()).isEqualTo(2.0);
    assertThat(report.get("structures")).isEmpty();
  }

  @Test
  void retriesUseAnyShortWindowAndExcludeAlgorithmsAndMissingSessions() throws Exception {
    generations("pila", 5, 3);
    for (int p = 0; p < 5; p++) {
      events.save(event(p, "pila", "s-" + p, inside.plusSeconds(1000)));
      for (int a = 0; a < 3; a++) {
        var run = event(p, "cola", "s-" + p, inside);
        run.setKind("algorithm");
        events.save(run);
        events.save(event(p, "arbol_avl", null, inside));
      }
    }
    JsonNode report = report();
    assertThat(report.get("retries")).hasSize(1);
    assertThat(report.at("/retries/0/sessionsWithRetries").asLong()).isEqualTo(5);
    assertThat(report.at("/retries/0/retryRate").asDouble()).isEqualTo(1.0);
    assertThat(report.at("/retries/0/highlighted").asBoolean()).isTrue();
  }

  @Test
  void sessionIdCollisionsDoNotCombineDifferentPeople() throws Exception {
    for (int p = 0; p < 5; p++) events.save(event(p, "pila", "same-session", inside));
    assertThat(report().at("/retries/0/retryRate").asDouble()).isZero();
    assertThat(report().at("/retries/0/sessions").asLong()).isEqualTo(5);
  }

  @Test
  void adminSeesReportButStudentAnonymousAndExpiredTokenDoNot() throws Exception {
    String expired =
        io.jsonwebtoken.Jwts.builder()
            .subject(TEACHER_EMAIL)
            .claim("roles", List.of("ROLE_TEACHER"))
            .claim("typ", "access")
            .issuedAt(java.util.Date.from(Instant.parse("2020-01-01T00:00:00Z")))
            .expiration(java.util.Date.from(Instant.parse("2020-01-01T00:15:00Z")))
            .signWith(
                io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .compact();
    mockMvc
        .perform(get(url("report")).header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk());
    for (String path : List.of("report", "report.csv")) {
      mockMvc.perform(get(url(path))).andExpect(status().isUnauthorized());
      mockMvc
          .perform(get(url(path)).header("Authorization", "Bearer " + expired))
          .andExpect(status().isUnauthorized());
      mockMvc
          .perform(get(url(path)).header("Authorization", "Bearer invalid-token"))
          .andExpect(status().isUnauthorized());
      mockMvc
          .perform(get(url(path)).header("Authorization", "Bearer " + student))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void revocationAppliesToExistingTokenAndExportImmediately() throws Exception {
    Long teacherId = users.findByEmail(TEACHER_EMAIL).orElseThrow().getId();
    mockMvc
        .perform(
            delete("/api/admin/courses/" + course.getCode() + "/teachers/" + teacherId)
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isNoContent());
    for (String path : List.of("report", "report.csv"))
      mockMvc
          .perform(get(url(path)).header("Authorization", "Bearer " + teacher))
          .andExpect(status().isForbidden());
    String listed =
        mockMvc
            .perform(get("/api/analytics/courses").header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(listed).doesNotContain(course.getCode());
    mockMvc
        .perform(
            put("/api/admin/courses/" + course.getCode() + "/teachers/" + teacherId)
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isNoContent());
    report();
  }

  @Test
  void assignmentRequiresAdministratorAndTeacherRole() throws Exception {
    Long studentId = users.findByEmail(STUDENT_EMAIL).orElseThrow().getId();
    String path = "/api/admin/courses/" + course.getCode() + "/teachers/" + studentId;
    mockMvc
        .perform(put(path).header("Authorization", "Bearer " + teacher))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(put(path).header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsIndividualRepeatedAndInvalidDateFilters() throws Exception {
    for (String extra :
        List.of("&studentId=1", "&minParticipants=1", "&courseCode=OTHER", "&from=2026-04-02")) {
      mockMvc
          .perform(get(url("report") + extra).header("Authorization", "Bearer " + teacher))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(
            get(url("report").replace("2026-04-30", "2026-04-31"))
                .header("Authorization", "Bearer " + teacher))
        .andExpect(status().isBadRequest());
  }

  @Test
  void legacyEndpointsCannotBypassSuppression() throws Exception {
    for (String path : List.of("summary", "events", "retries")) {
      String body =
          mockMvc
              .perform(get("/api/analytics/" + path).header("Authorization", "Bearer " + teacher))
              .andExpect(status().isGone())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain("sessionId", "pseudonym", "promptText", "totalGenerations");
    }
  }

  @Test
  void progressIsBoundedMonotonicAndOwnedByTheAuthenticatedStudent() throws Exception {
    String execution =
        mockMvc
            .perform(
                post("/api/algorithm/steps")
                    .header("Authorization", "Bearer " + student)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"tree\",\"subtype\":\"avl\",\"operation\":\"insert\",\"values\":[10,5,3]}"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Analytics-Execution"))
            .andReturn()
            .getResponse()
            .getHeader("X-Analytics-Execution");
    for (int steps : List.of(2, 2, 1, 99999, -1)) {
      mockMvc
          .perform(
              post("/api/assistant/events")
                  .header("Authorization", "Bearer " + student)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json(
                          java.util.Map.of(
                              "event",
                              "algorithm_progress",
                              "executionId",
                              execution,
                              "stepCount",
                              steps))))
          .andExpect(status().isAccepted());
    }
    mockMvc
        .perform(
            post("/api/assistant/events")
                .header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        java.util.Map.of(
                            "event",
                            "algorithm_progress",
                            "executionId",
                            execution,
                            "stepCount",
                            3))))
        .andExpect(status().isAccepted());
    assertThat(events.findAll().stream().filter(e -> execution.equals(e.getExecutionId())).toList())
        .singleElement()
        .satisfies(e -> assertThat(e.getCompletedSteps()).isEqualTo(2));
  }
}
