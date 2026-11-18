package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import com.vista.pdg.telemetry.service.Pseudonymizer;
import com.vista.pdg.telemetry.service.StructureKind;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** HU-36 · CA-5 a CA-10: la analítica del grupo con quizzes, estudio y práctica reales. */
class QuizAnalyticsTest extends QuizTestSupport {
  @Autowired private GenerationEventRepository events;
  @Autowired private Pseudonymizer pseudonymizer;

  private JsonNode report(String token, String query) throws Exception {
    return read(
        mockMvc
            .perform(get("/api/analytics/report?" + query).header("Authorization", token))
            .andExpect(status().isOk()));
  }

  private void answerTimed(String token, long quiz, int position, int option, long ms)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("selectedOption", option);
    body.put("elapsedMs", ms);
    mockMvc
        .perform(
            authed(put("/api/quizzes/{id}/attempt/answers/{p}", quiz, position), token)
                .content(json(body)))
        .andExpect(status().isOk());
  }

  @Test
  void groupReportRelatesQuizzesStudyAndPracticeByTopic() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    List<AuthResponse> students = new ArrayList<>();
    for (int i = 0; i < 5; i++) students.add(student(course, "hu36a" + i));

    // Con todo el periodo y sin actividad todavía, el grupo no alcanza el umbral.
    JsonNode empty = report(teacher, "courseCode=" + course);
    assertThat(empty.path("status").asText()).isEqualTo("SUPPRESSED");
    assertThat(empty.path("wholeTerm").asBoolean()).isTrue();
    assertThat(empty.path("topics")).isEmpty();

    long quiz = publishedQuiz(teacher, quizBody(course, 2, 5));
    JsonNode bank = bank(teacher, quiz);
    for (int s = 0; s < students.size(); s++) {
      String token = bearer(students.get(s));
      if (s == 0) {
        // El primer estudiante practicó la inserción AVL en VISTA antes del quiz.
        events.save(
            GenerationEvent.builder()
                .pseudonym(
                    pseudonymizer.pseudonymFor(
                        users.findByEmail(students.get(s).email()).orElseThrow().getId()))
                .courseCode(course)
                .termCode("2026-1")
                .studentParticipant(true)
                .structureType(StructureKind.of("tree", "avl"))
                .subtype("avl")
                .algorithm("insert")
                .kind("algorithm")
                .createdAt(now().minus(2, ChronoUnit.HOURS))
                .build());
      }
      JsonNode attempt = start(token, quiz);
      JsonNode questions = attempt.path("questions");
      // El servidor no acepta más tiempo del que lleva el intento: el reloj avanza un minuto.
      MutableClockConfig.CLOCK.advance(Duration.ofMinutes(1));
      answerTimed(token, quiz, 1, optionIndex(bank, questions.get(0), true), 30_000);
      answerTimed(token, quiz, 2, optionIndex(bank, questions.get(1), false), 50_000);
      mockMvc
          .perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), token))
          .andExpect(status().isOk());
    }

    // Un quiz de estudio respondido también cuenta para el tema.
    String learner = bearer(students.get(1));
    long study =
        read(mockMvc
                .perform(
                    authed(post("/api/study-quizzes"), learner)
                        .content(
                            json(Map.of("title", "", "topics", List.of(AVL), "questionCount", 1))))
                .andExpect(status().isCreated()))
            .path("id")
            .asLong();
    mockMvc
        .perform(authed(post("/api/study-quizzes/{id}/generate", study), learner))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            authed(put("/api/study-quizzes/{id}/questions/1/answer", study), learner)
                .content(json(Map.of("selectedOption", 0, "elapsedMs", 12_000))))
        .andExpect(status().isOk());

    JsonNode report = report(teacher, "courseCode=" + course);
    assertThat(report.path("status").asText()).isEqualTo("AVAILABLE");
    assertThat(report.path("activeStudents").asInt()).isEqualTo(5);
    assertThat(report.path("enrolledStudents").asInt()).isEqualTo(5);
    JsonNode overview = report.path("overview");
    assertThat(overview.path("quizAnswers").asLong()).isEqualTo(10);
    assertThat(overview.path("quizAccuracy").asDouble()).isEqualTo(.5);
    assertThat(overview.path("finishedAttempts").asLong()).isEqualTo(5);
    assertThat(overview.path("medianSeconds").asDouble()).isEqualTo(40.0);
    assertThat(overview.path("timedShare").asDouble()).isEqualTo(1.0);
    assertThat(overview.path("studyAnswers").asLong()).isEqualTo(1);
    assertThat(overview.path("algorithmExecutions").asLong()).isEqualTo(1);

    List<String> keys = new ArrayList<>();
    report.path("topics").forEach(t -> keys.add(t.path("topicKey").asText()));
    assertThat(keys).contains(AVL, BFS);
    JsonNode avl = null;
    for (JsonNode t : report.path("topics")) if (t.path("topicKey").asText().equals(AVL)) avl = t;
    assertThat(avl).isNotNull();
    assertThat(avl.path("practiceExecutions").asLong()).isEqualTo(1);
    assertThat(avl.path("studyAnswers").asLong()).isEqualTo(1);
    assertThat(avl.path("level").asText()).isIn("DIFICIL", "EN_PROGRESO", "DOMINADO");
    assertThat(report.path("quizzes").get(0).path("finished").asLong()).isEqualTo(5);
    assertThat(report.path("quizzes").get(0).path("averageGrade").asDouble()).isEqualTo(2.5);
    assertThat(report.toString()).doesNotContain("@", "Estudiante ", "pseudonym", "studentId");

    // Filtro opcional de quiz y lista para el selector.
    mockMvc
        .perform(
            get("/api/analytics/quizzes?courseCode=" + course).header("Authorization", teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(quiz));
    JsonNode filtered = report(teacher, "courseCode=" + course + "&quizId=" + quiz);
    assertThat(filtered.path("quiz").path("id").asLong()).isEqualTo(quiz);
    assertThat(filtered.path("overview").path("quizAnswers").asLong()).isEqualTo(10);
    String other = newCourse();
    long foreign = publishedQuiz(admin(), quizBody(other, 1, 1));
    mockMvc
        .perform(
            get("/api/analytics/report?courseCode=" + course + "&quizId=" + foreign)
                .header("Authorization", teacher))
        .andExpect(status().isBadRequest());

    // Un periodo sin actividad vuelve a quedar por debajo del umbral.
    JsonNode past = report(teacher, "courseCode=" + course + "&from=2020-01-01&to=2020-01-31");
    assertThat(past.path("status").asText()).isEqualTo("SUPPRESSED");

    String csv =
        mockMvc
            .perform(
                get("/api/analytics/report.csv?courseCode=" + course)
                    .header("Authorization", teacher))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"vista-analitica-" + course + "-2026-1.csv\""))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv).contains("\"Resumen\"", "\"Temas\"", "\"Quizzes\"", "Todo el periodo");
  }
}
