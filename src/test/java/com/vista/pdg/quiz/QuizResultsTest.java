package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** HU-34 · CA-8 y CA-9: panel de resultados del docente y reportes CSV/XLSX. */
class QuizResultsTest extends QuizTestSupport {

  @Test
  void teacherSeesEveryEnrolledStudentWithStatsAndExportsTheReport() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    AuthResponse perfect = student(course, "qzra");
    AuthResponse partial = student(course, "qzrb");
    AuthResponse running = student(course, "qzrc");
    AuthResponse absent = student(course, "qzrd");
    long quiz = publishedQuiz(teacher, quizBody(course, 2, 4));
    JsonNode bank = bank(teacher, quiz);

    // Sin entregas todavía: todos «sin presentar» y estadísticas vacías.
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}/results", quiz), teacher))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.rows.length()").value(4))
        .andExpect(jsonPath("$.stats.finished").value(0))
        .andExpect(jsonPath("$.stats.notStarted").value(4))
        .andExpect(jsonPath("$.stats.average").doesNotExist());

    JsonNode first = start(bearer(perfect), quiz);
    for (int p = 1; p <= 2; p++) {
      answer(bearer(perfect), quiz, p, optionIndex(bank, first.path("questions").get(p - 1), true));
    }
    mockMvc.perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), bearer(perfect)));

    JsonNode second = start(bearer(partial), quiz);
    answer(bearer(partial), quiz, 1, optionIndex(bank, second.path("questions").get(0), false));
    mockMvc.perform(
        authed(post("/api/quizzes/{id}/attempt/integrity", quiz), bearer(partial))
            .content(json(Map.of("type", "COPY"))));
    // Vence sin entregar: el panel lo cierra al consultarse, sin esperar al estudiante.
    MutableClockConfig.CLOCK.advance(Duration.ofMinutes(31));
    start(bearer(running), quiz);

    JsonNode results =
        read(
            mockMvc
                .perform(authed(get("/api/quizzes/manage/{id}/results", quiz), teacher))
                .andExpect(status().isOk()));
    Map<String, JsonNode> rows = new HashMap<>();
    results.path("rows").forEach(row -> rows.put(row.path("email").asText(), row));

    JsonNode top = rows.get(perfect.email());
    assertThat(top.path("status").asText()).isEqualTo("SUBMITTED");
    assertThat(top.path("score").asInt()).isEqualTo(2);
    assertThat(top.path("grade").asDouble()).isEqualTo(5.0);
    assertThat(top.path("answers").findValuesAsText("")).isEmpty();
    assertThat(top.path("answers").get(0).asText()).isEqualTo("CORRECT");

    JsonNode low = rows.get(partial.email());
    assertThat(low.path("status").asText()).isEqualTo("EXPIRED");
    assertThat(low.path("grade").asDouble()).isEqualTo(0.0);
    assertThat(low.path("integrityEvents").asInt()).isEqualTo(1);
    assertThat(low.path("answers").get(0).asText()).isEqualTo("INCORRECT");
    assertThat(low.path("answers").get(1).asText()).isEqualTo("UNANSWERED");

    JsonNode open = rows.get(running.email());
    assertThat(open.path("status").asText()).isEqualTo("IN_PROGRESS");
    assertThat(open.path("grade").isNull()).isTrue();
    assertThat(open.path("answers")).isEmpty();
    assertThat(rows.get(absent.email()).path("status").asText()).isEqualTo("NOT_STARTED");

    JsonNode stats = results.path("stats");
    assertThat(stats.path("average").asDouble()).isEqualTo(2.5);
    assertThat(stats.path("highest").asDouble()).isEqualTo(5.0);
    assertThat(stats.path("lowest").asDouble()).isEqualTo(0.0);
    assertThat(stats.path("finished").asInt()).isEqualTo(2);
    assertThat(stats.path("inProgress").asInt()).isEqualTo(1);
    assertThat(stats.path("notStarted").asInt()).isEqualTo(1);
    assertThat(stats.path("enrolled").asInt()).isEqualTo(4);
    assertThat(results.path("quiz").path("attemptsFinished").asInt()).isEqualTo(2);

    String csv =
        mockMvc
            .perform(authed(get("/api/quizzes/manage/{id}/results.csv", quiz), teacher))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"vista-quiz-" + quiz + "-resultados.csv\""))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv).startsWith("﻿\"Nombre\",\"Correo\",\"Estado\"").contains("\"P1\",\"P2\"");
    assertThat(csv).contains(perfect.email() + "\",\"Entregado\",2,2,5.0,0,");
    assertThat(csv).contains("\"Correcta\",\"Correcta\"");
    assertThat(csv).contains(partial.email() + "\",\"Tiempo agotado\",0,2,0.0,1,");
    assertThat(csv).contains("\"Incorrecta\",\"Sin responder\"");
    assertThat(csv).contains(absent.email() + "\",\"Sin presentar\",,,,,");

    byte[] xlsx =
        mockMvc
            .perform(authed(get("/api/quizzes/manage/{id}/results.xlsx", quiz), teacher))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    Map<String, String> parts = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    assertThat(parts)
        .containsKeys(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/worksheets/sheet1.xml");
    String sheet = parts.get("xl/worksheets/sheet1.xml");
    assertThat(sheet).contains(perfect.email()).contains("Tiempo agotado").contains("<v>5.0</v>");
    assertThat(sheet).contains("<c r=\"A1\" t=\"inlineStr\">").contains("<c r=\"K1\"");
  }

  @Test
  void resultsKeepAStudentWhoLeftTheCourseAndRejectOtherRoles() throws Exception {
    String course = newCourse();
    String admin = admin();
    AuthResponse mover = student(course, "qzmove");
    long quiz = publishedQuiz(admin, quizBody(course, 2, 1));
    start(bearer(mover), quiz);
    mockMvc.perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), bearer(mover)));
    Long id = users.findByEmail(mover.email()).orElseThrow().getId();
    mockMvc
        .perform(delete("/api/admin/students/{id}/course", id).header("Authorization", admin))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}/results", quiz), admin))
        .andExpect(jsonPath("$.stats.enrolled").value(0))
        .andExpect(jsonPath("$.rows.length()").value(1))
        .andExpect(jsonPath("$.rows[0].email").value(mover.email()))
        .andExpect(jsonPath("$.rows[0].status").value("SUBMITTED"));

    // Sin matrícula deja de ver el quiz; un docente sin el curso y un estudiante no ven resultados.
    mockMvc
        .perform(authed(get("/api/quizzes"), bearer(mover)))
        .andExpect(jsonPath("$.quizzes.length()").value(0));
    for (String path : new String[] {"/results", "/results.csv", "/results.xlsx"}) {
      mockMvc
          .perform(authed(get("/api/quizzes/manage/" + quiz + path), teacher()))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(authed(get("/api/quizzes/manage/" + quiz + path), bearer(mover)))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(get("/api/quizzes/manage/" + quiz + path))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void deletingAStudentAccountRemovesTheirAttemptWithoutBreakingTheQuiz() throws Exception {
    String course = newCourse();
    String admin = admin();
    AuthResponse leaving = student(course, "qzdel");
    long quiz = publishedQuiz(admin, quizBody(course, 2, 1));
    start(bearer(leaving), quiz);
    Long id = users.findByEmail(leaving.email()).orElseThrow().getId();

    mockMvc
        .perform(delete("/api/admin/users/{id}", id).header("Authorization", admin))
        .andExpect(status().is2xxSuccessful());
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}/results", quiz), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.length()").value(0));
  }
}
