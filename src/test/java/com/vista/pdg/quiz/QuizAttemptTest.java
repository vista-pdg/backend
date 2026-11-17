package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** HU-34 · CA-3 a CA-7: visibilidad por curso, intento, tiempos, incidentes y nota. */
class QuizAttemptTest extends QuizTestSupport {

  @Test
  void onlyStudentsOfTheCourseSeeAndTakeAPublishedQuiz() throws Exception {
    String course = newCourse();
    String other = newCourse();
    String admin = admin();
    String insider = bearer(student(course, "qzin"));
    String outsider = bearer(student(other, "qzout"));

    long draft = createQuiz(admin, quizBody(course, 2, 2));
    mockMvc
        .perform(authed(get("/api/quizzes"), insider))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quizzes.length()").value(0));
    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt", draft), insider))
        .andExpect(status().isNotFound());

    long quiz = publishedQuiz(admin, quizBody(course, 2, 2));
    mockMvc
        .perform(authed(get("/api/quizzes"), insider))
        .andExpect(jsonPath("$.courseCode").value(course))
        .andExpect(jsonPath("$.quizzes.length()").value(1))
        .andExpect(jsonPath("$.quizzes[0].id").value(quiz))
        .andExpect(jsonPath("$.quizzes[0].state").value("OPEN"))
        .andExpect(jsonPath("$.quizzes[0].questionCount").value(2))
        .andExpect(jsonPath("$.quizzes[0].topics[0]").value("Árbol AVL — AVL · Inserción"))
        .andExpect(jsonPath("$.quizzes[0].result").doesNotExist());

    // Otro curso: ni en la lista ni por API directa. Docentes y administradores tampoco presentan.
    mockMvc
        .perform(authed(get("/api/quizzes"), outsider))
        .andExpect(jsonPath("$.quizzes.length()").value(0));
    for (String actor : new String[] {outsider, admin, teacher()}) {
      mockMvc
          .perform(authed(post("/api/quizzes/{id}/attempt", quiz), actor))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
      mockMvc
          .perform(authed(get("/api/quizzes/{id}/attempt", quiz), actor))
          .andExpect(status().isNotFound());
      answer(actor, quiz, 1, 0).andExpect(status().isNotFound());
      mockMvc
          .perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), actor))
          .andExpect(status().isNotFound());
    }
    mockMvc
        .perform(authed(get("/api/quizzes"), admin))
        .andExpect(jsonPath("$.quizzes.length()").value(0));
  }

  @Test
  void attemptHidesAnswersIsResumableAndGradesOnSubmit() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzflow"));
    long quiz = publishedQuiz(admin, quizBody(course, 3, 1));
    JsonNode bank = bank(admin, quiz);

    mockMvc
        .perform(authed(get("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ATTEMPT_NOT_FOUND"));

    String raw =
        mockMvc
            .perform(authed(post("/api/quizzes/{id}/attempt", quiz), student))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(raw).doesNotContain("correctIndex").doesNotContain("explanation");
    JsonNode attempt = objectMapper.readTree(raw);
    assertThat(attempt.path("status").asText()).isEqualTo("IN_PROGRESS");
    assertThat(attempt.path("questions")).hasSize(3);
    assertThat(attempt.path("result").isNull()).isTrue();
    Instant deadline = Instant.parse(attempt.path("deadline").asText());
    assertThat(Duration.between(Instant.parse(attempt.path("startedAt").asText()), deadline))
        .isEqualTo(Duration.ofMinutes(30));

    // Dos aciertos y un error; la primera respuesta se cambia antes de entregar.
    JsonNode q1 = attempt.path("questions").get(0);
    answer(student, quiz, 1, optionIndex(bank, q1, false)).andExpect(status().isOk());
    answer(student, quiz, 1, optionIndex(bank, q1, true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questions[0].selectedOption").value(optionIndex(bank, q1, true)));
    answer(student, quiz, 2, optionIndex(bank, attempt.path("questions").get(1), true))
        .andExpect(status().isOk());
    answer(student, quiz, 3, optionIndex(bank, attempt.path("questions").get(2), false))
        .andExpect(status().isOk());
    answer(student, quiz, 9, 0).andExpect(status().isBadRequest());
    answer(student, quiz, 1, 7).andExpect(status().isUnprocessableEntity());

    // Recargar la página retoma el mismo intento con lo ya respondido.
    JsonNode resumed = start(student, quiz);
    assertThat(resumed.path("startedAt").asText()).isEqualTo(attempt.path("startedAt").asText());
    assertThat(resumed.path("questions").get(0).path("statement").asText())
        .isEqualTo(q1.path("statement").asText());
    assertThat(resumed.path("questions").get(1).path("selectedOption").isNull()).isFalse();
    mockMvc
        .perform(authed(get("/api/quizzes"), student))
        .andExpect(jsonPath("$.quizzes[0].state").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.quizzes[0].deadline").exists());

    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.questions.length()").value(0))
        .andExpect(jsonPath("$.result.gradeVisible").value(true))
        .andExpect(jsonPath("$.result.score").value(2))
        .andExpect(jsonPath("$.result.total").value(3))
        .andExpect(jsonPath("$.result.grade").value(3.3));

    // Entregado: no se aceptan más respuestas y entregar de nuevo no cambia nada.
    answer(student, quiz, 3, 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ATTEMPT_CLOSED"));
    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), student))
        .andExpect(jsonPath("$.result.score").value(2));
    mockMvc
        .perform(authed(get("/api/quizzes"), student))
        .andExpect(jsonPath("$.quizzes[0].state").value("FINISHED"))
        .andExpect(jsonPath("$.quizzes[0].result.grade").value(3.3));
  }

  @Test
  void gradeStaysHiddenUntilTheTeacherAllowsIt() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzhide"));
    Map<String, Object> body = quizBody(course, 2, 1);
    body.put("showGrade", false);
    long quiz = publishedQuiz(admin, body);

    start(student, quiz);
    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), student))
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.result.gradeVisible").value(false))
        .andExpect(jsonPath("$.result.score").doesNotExist())
        .andExpect(jsonPath("$.result.grade").doesNotExist());
    mockMvc
        .perform(authed(get("/api/quizzes"), student))
        .andExpect(jsonPath("$.quizzes[0].state").value("FINISHED"))
        .andExpect(jsonPath("$.quizzes[0].result.grade").doesNotExist());

    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{id}/grade-visibility", quiz), admin)
                .content(json(Map.of("showGrade", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showGrade").value(true));
    mockMvc
        .perform(authed(get("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(jsonPath("$.result.gradeVisible").value(true))
        .andExpect(jsonPath("$.result.score").value(0))
        .andExpect(jsonPath("$.result.grade").value(0.0));
    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{id}/grade-visibility", quiz), admin).content("{}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void windowControlsWhenAnAttemptCanStart() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzwin"));
    Map<String, Object> body = quizBody(course, 2, 1);
    body.put("opensAt", now().plus(1, ChronoUnit.HOURS).toString());
    body.put("closesAt", now().plus(2, ChronoUnit.HOURS).toString());
    long quiz = publishedQuiz(admin, body);

    mockMvc
        .perform(authed(get("/api/quizzes"), student))
        .andExpect(jsonPath("$.quizzes[0].state").value("UPCOMING"));
    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUIZ_NOT_OPEN"));
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}", quiz), admin))
        .andExpect(jsonPath("$.quiz.phase").value("SCHEDULED"));

    MutableClockConfig.CLOCK.advance(Duration.ofHours(3));
    mockMvc
        .perform(authed(get("/api/quizzes"), student))
        .andExpect(jsonPath("$.quizzes[0].state").value("MISSED"));
    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUIZ_CLOSED"));
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}", quiz), admin))
        .andExpect(jsonPath("$.quiz.phase").value("CLOSED"));
  }

  @Test
  void expiredAttemptIsClosedByTheServerAndGradedWithWhatWasAnswered() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzexp"));
    long quiz = publishedQuiz(admin, quizBody(course, 3, 1));
    JsonNode bank = bank(admin, quiz);

    JsonNode attempt = start(student, quiz);
    answer(student, quiz, 1, optionIndex(bank, attempt.path("questions").get(0), true))
        .andExpect(status().isOk());

    MutableClockConfig.CLOCK.advance(Duration.ofMinutes(31));
    answer(student, quiz, 2, optionIndex(bank, attempt.path("questions").get(1), true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ATTEMPT_CLOSED"));
    mockMvc
        .perform(
            authed(post("/api/quizzes/{id}/attempt/integrity", quiz), student)
                .content(json(Map.of("type", "FOCUS_LOST"))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(authed(get("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(jsonPath("$.status").value("EXPIRED"))
        .andExpect(jsonPath("$.result.score").value(1))
        .andExpect(jsonPath("$.result.total").value(3))
        .andExpect(jsonPath("$.result.grade").value(1.7))
        .andExpect(jsonPath("$.result.finishedAt").value(attempt.path("deadline").asText()));
    // Entregar tarde no convierte el vencimiento en una entrega normal.
    mockMvc
        .perform(authed(post("/api/quizzes/{id}/attempt/submit", quiz), student))
        .andExpect(jsonPath("$.status").value("EXPIRED"));
  }

  @Test
  void deadlineNeverExceedsTheClosingTime() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzcap"));
    Map<String, Object> body = quizBody(course, 2, 1);
    Instant closes = now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
    body.put("closesAt", closes.toString());
    long quiz = publishedQuiz(admin, body);

    JsonNode attempt = start(student, quiz);
    assertThat(Instant.parse(attempt.path("deadline").asText())).isEqualTo(closes);
  }

  @Test
  void integrityIncidentsAreCountedPerAttempt() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzint"));
    long quiz = publishedQuiz(admin, quizBody(course, 2, 1));
    start(student, quiz);

    for (int expected = 1; expected <= 3; expected++) {
      mockMvc
          .perform(
              authed(post("/api/quizzes/{id}/attempt/integrity", quiz), student)
                  .content(json(Map.of("type", "FOCUS_LOST"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.integrityEvents").value(expected));
    }
    mockMvc
        .perform(
            authed(post("/api/quizzes/{id}/attempt/integrity", quiz), student)
                .content(json(Map.of("type", ""))))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(authed(get("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(jsonPath("$.integrityEvents").value(3));
  }

  @Test
  void simultaneousIncidentsAreAllCounted() throws Exception {
    String course = newCourse();
    String admin = admin();
    String student = bearer(student(course, "qzrace"));
    long quiz = publishedQuiz(admin, quizBody(course, 2, 1));
    start(student, quiz);

    int parallel = 12;
    var pool = java.util.concurrent.Executors.newFixedThreadPool(parallel);
    var ready = new java.util.concurrent.CountDownLatch(1);
    var calls = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
    for (int i = 0; i < parallel; i++) {
      calls.add(
          pool.submit(
              () -> {
                ready.await();
                return mockMvc
                    .perform(
                        authed(post("/api/quizzes/{id}/attempt/integrity", quiz), student)
                            .content(json(Map.of("type", "COPY"))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              }));
    }
    ready.countDown();
    for (var call : calls) assertThat(call.get()).isEqualTo(200);
    pool.shutdown();

    mockMvc
        .perform(authed(get("/api/quizzes/{id}/attempt", quiz), student))
        .andExpect(jsonPath("$.integrityEvents").value(parallel));
  }

  @Test
  void studentsReceiveDifferentQuestionsWhileTheBankLasts() throws Exception {
    String course = newCourse();
    String admin = admin();
    long quiz = publishedQuiz(admin, quizBody(course, 2, 3));

    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      JsonNode attempt = start(bearer(student(course, "qzuniq")), quiz);
      for (JsonNode question : attempt.path("questions")) {
        assertThat(
                seen.add(question.path("scenario").asText() + question.path("statement").asText()))
            .as("pregunta repetida entre estudiantes")
            .isTrue();
      }
      // Con dos temas y dos preguntas, cada estudiante recibe una de cada tema.
      assertThat(attempt.path("questions").findValuesAsText("topicLabel")).doesNotHaveDuplicates();
    }
    assertThat(seen).hasSize(6);
    assertThat(bank(admin, quiz).findValues("timesAssigned")).allMatch(times -> times.asInt() == 1);
    // Un cuarto estudiante agota el banco: recibe preguntas ya usadas en lugar de quedarse sin
    // quiz.
    assertThat(start(bearer(student(course, "qzuniq")), quiz).path("questions")).hasSize(2);

    mockMvc
        .perform(authed(delete("/api/quizzes/manage/{id}", quiz), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUIZ_HAS_ATTEMPTS"));
  }
}
