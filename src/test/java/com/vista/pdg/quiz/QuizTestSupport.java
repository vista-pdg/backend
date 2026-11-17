package com.vista.pdg.quiz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.testsupport.FakeLlmConfig;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Atajos de las pruebas de quizzes: cada prueba trabaja sobre un curso propio. */
abstract class QuizTestSupport extends IntegrationTestSupport {
  private static final AtomicInteger COURSE_SEQ = new AtomicInteger();
  static final String AVL = "tree/avl/insert";
  static final String BFS = "graph/simple/bfs";

  @Autowired protected UserRepository users;

  @AfterEach
  void restoreDoubles() {
    MutableClockConfig.CLOCK.reset();
    FakeLlmConfig.QUIZ_RESPONDER.set(null);
  }

  protected Instant now() {
    return MutableClockConfig.CLOCK.instant();
  }

  protected String admin() throws Exception {
    return "Bearer " + login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
  }

  protected String teacher() throws Exception {
    return "Bearer " + login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
  }

  protected String newCourse() throws Exception {
    String code = "QZ-" + COURSE_SEQ.incrementAndGet();
    mockMvc
        .perform(
            authed(post("/api/admin/courses"), admin())
                .content(json(Map.of("code", code, "name", "Curso " + code, "termCode", "2026-1"))))
        .andExpect(status().isCreated());
    return code;
  }

  protected void assignTeacher(String course) throws Exception {
    Long id = users.findByEmail(TEACHER_EMAIL).orElseThrow().getId();
    mockMvc
        .perform(
            put("/api/admin/courses/{code}/teachers/{id}", course, id)
                .header("Authorization", admin()))
        .andExpect(status().isNoContent());
  }

  /** Registra un estudiante en el curso dado; devuelve la respuesta de autenticación. */
  protected AuthResponse student(String course, String prefix) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            verifiedRegistration(
                                "Estudiante " + prefix,
                                uniqueEmail(prefix),
                                "clave12345",
                                "clave12345",
                                course))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, AuthResponse.class);
  }

  protected static String bearer(AuthResponse auth) {
    return "Bearer " + auth.accessToken();
  }

  protected MockHttpServletRequestBuilder authed(
      MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON);
  }

  protected Map<String, Object> quizBody(String course, int perStudent, Integer students) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("courseCode", course);
    body.put("title", "Quiz de prueba");
    body.put("topics", List.of(AVL, BFS));
    body.put("questionsPerStudent", perStudent);
    body.put("studentCount", students);
    body.put("timeLimitMinutes", 30);
    body.put("opensAt", now().minus(1, ChronoUnit.HOURS).toString());
    body.put("closesAt", now().plus(2, ChronoUnit.DAYS).toString());
    body.put("showGrade", true);
    return body;
  }

  protected ResultActions create(String token, Map<String, Object> body) throws Exception {
    return mockMvc.perform(authed(post("/api/quizzes/manage"), token).content(json(body)));
  }

  protected long createQuiz(String token, Map<String, Object> body) throws Exception {
    return read(create(token, body).andExpect(status().isCreated())).path("id").asLong();
  }

  protected void fillBank(String token, long quiz) throws Exception {
    for (int i = 0; i < 200; i++) {
      JsonNode batch =
          read(
              mockMvc
                  .perform(authed(post("/api/quizzes/manage/{id}/bank/generate", quiz), token))
                  .andExpect(status().isOk()));
      if (batch.path("complete").asBoolean()) return;
    }
    throw new AssertionError("El banco no se completó");
  }

  /** Quiz publicado con el banco lleno. */
  protected long publishedQuiz(String token, Map<String, Object> body) throws Exception {
    long quiz = createQuiz(token, body);
    fillBank(token, quiz);
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", quiz), token))
        .andExpect(status().isOk());
    return quiz;
  }

  protected JsonNode read(ResultActions result) throws Exception {
    return objectMapper.readTree(
        result
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
  }

  protected JsonNode bank(String token, long quiz) throws Exception {
    return read(mockMvc
            .perform(authed(get("/api/quizzes/manage/{id}", quiz), token))
            .andExpect(status().isOk()))
        .path("questions");
  }

  protected JsonNode start(String student, long quiz) throws Exception {
    return read(
        mockMvc
            .perform(authed(post("/api/quizzes/{id}/attempt", quiz), student))
            .andExpect(status().isOk()));
  }

  protected ResultActions answer(String student, long quiz, int position, Integer option)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("selectedOption", option);
    return mockMvc.perform(
        authed(put("/api/quizzes/{id}/attempt/answers/{p}", quiz, position), student)
            .content(json(body)));
  }

  /** Índice, en el orden barajado del estudiante, de la opción correcta (o de una incorrecta). */
  protected int optionIndex(JsonNode bank, JsonNode question, boolean correct) {
    for (JsonNode candidate : bank) {
      if (candidate.path("statement").asText().equals(question.path("statement").asText())
          && candidate.path("scenario").asText().equals(question.path("scenario").asText())) {
        String right =
            candidate.path("options").get(candidate.path("correctIndex").asInt()).asText();
        for (int i = 0; i < 4; i++) {
          if (question.path("options").get(i).asText().equals(right) == correct) return i;
        }
      }
    }
    throw new AssertionError("La pregunta no está en el banco");
  }
}
