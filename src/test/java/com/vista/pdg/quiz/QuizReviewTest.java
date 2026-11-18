package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HU-35 · CA-1 y CA-4: preparación en VISTA durante el intento y revisión de solo lectura. */
class QuizReviewTest extends QuizTestSupport {

  private JsonNode review(String student, long quiz, int status) throws Exception {
    return read(
        mockMvc
            .perform(authed(get("/api/quizzes/{id}/attempt/review", quiz), student))
            .andExpect(status().is(status)));
  }

  private JsonNode listed(String student, long quiz) throws Exception {
    for (JsonNode q :
        read(mockMvc.perform(authed(get("/api/quizzes"), student)).andExpect(status().isOk()))
            .path("quizzes")) {
      if (q.path("id").asLong() == quiz) return q;
    }
    throw new AssertionError("El quiz no está en la lista");
  }

  @Test
  void attemptQuestionsCarryTheValidatedVistaSetupButNeverTheAnswer() throws Exception {
    String course = newCourse();
    String admin = admin();
    long quiz = publishedQuiz(admin, quizBody(course, 2, 1));
    String student = bearer(student(course, "qzsetup"));
    JsonNode attempt = start(student, quiz);
    for (JsonNode q : attempt.path("questions")) {
      JsonNode setup = q.path("setup");
      assertThat(setup.path("chatPrompt").asText()).startsWith("Construye desde cero");
      assertThat(setup.path("algorithm").asText()).isIn(AVL, BFS);
      assertThat(q.has("correctOption")).isFalse();
      assertThat(q.has("explanation")).isFalse();
      assertThat(q.has("hint")).isFalse();
    }
  }

  @Test
  void reviewOpensWhenTheQuizClosesAndTheGradeIsVisibleAndIsReadOnly() throws Exception {
    String course = newCourse();
    String admin = admin();
    Map<String, Object> body = quizBody(course, 2, 2);
    long quiz = publishedQuiz(admin, body);
    JsonNode bank = bank(admin, quiz);
    String student = bearer(student(course, "qzreview"));
    String late = bearer(student(course, "qzreviewlate"));

    JsonNode attempt = start(student, quiz);
    JsonNode first = attempt.path("questions").get(0);
    JsonNode second = attempt.path("questions").get(1);
    int right = optionIndex(bank, first, true);
    int wrong = optionIndex(bank, second, false);
    answer(student, quiz, 1, right).andExpect(status().isOk());
    answer(student, quiz, 2, wrong).andExpect(status().isOk());

    // Sin entregar: no hay revisión.
    review(student, quiz, 409);
    mockMvc
        .perform(post("/api/quizzes/{id}/attempt/submit", quiz).header("Authorization", student))
        .andExpect(status().isOk());

    // Entregado, pero el quiz sigue abierto para otros: aún no se revela nada.
    start(late, quiz);
    assertThat(review(student, quiz, 409).path("code").asText()).isEqualTo("REVIEW_NOT_YET");
    assertThat(listed(student, quiz).path("reviewAvailable").asBoolean()).isFalse();

    MutableClockConfig.CLOCK.advance(Duration.ofDays(3));
    assertThat(listed(student, quiz).path("reviewAvailable").asBoolean()).isTrue();
    JsonNode reviewed = review(student, quiz, 200);
    assertThat(reviewed.path("score").asInt()).isEqualTo(1);
    assertThat(reviewed.path("total").asInt()).isEqualTo(2);
    assertThat(reviewed.path("grade").asDouble()).isEqualTo(2.5);
    JsonNode q1 = reviewed.path("questions").get(0);
    JsonNode q2 = reviewed.path("questions").get(1);
    assertThat(q1.path("outcome").asText()).isEqualTo("CORRECT");
    assertThat(q1.path("selectedOption").asInt()).isEqualTo(right);
    assertThat(q1.path("correctOption").asInt()).isEqualTo(right);
    assertThat(q1.path("options")).isEqualTo(first.path("options"));
    assertThat(q2.path("outcome").asText()).isEqualTo("INCORRECT");
    assertThat(q2.path("selectedOption").asInt()).isEqualTo(wrong);
    assertThat(q2.path("correctOption").asInt()).isEqualTo(optionIndex(bank, second, true));
    assertThat(q2.path("explanation").asText()).isNotBlank();

    // El intento que vence sin respuestas se revisa como sin responder.
    JsonNode lateReview = review(late, quiz, 200);
    assertThat(lateReview.path("status").asText()).isEqualTo("EXPIRED");
    assertThat(lateReview.path("questions").get(0).path("outcome").asText())
        .isEqualTo("UNANSWERED");
    assertThat(lateReview.path("questions").get(0).path("selectedOption").isNull()).isTrue();

    // Solo lectura: la revisión no reabre el intento.
    answer(student, quiz, 1, wrong)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ATTEMPT_CLOSED"));
    assertThat(review(student, quiz, 200).path("score").asInt()).isEqualTo(1);

    // Nota oculta: no hay revisión, aunque el quiz haya cerrado.
    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{id}/grade-visibility", quiz), admin)
                .content(json(Map.of("showGrade", false))))
        .andExpect(status().isOk());
    assertThat(review(student, quiz, 409).path("code").asText()).isEqualTo("REVIEW_HIDDEN");
    assertThat(listed(student, quiz).path("reviewAvailable").asBoolean()).isFalse();
  }

  @Test
  void onlyTheAuthorOfTheAttemptCanReviewIt() throws Exception {
    String course = newCourse();
    String admin = admin();
    long quiz = publishedQuiz(admin, quizBody(course, 1, 2));
    String student = bearer(student(course, "qzrevown"));
    start(student, quiz);
    mockMvc
        .perform(post("/api/quizzes/{id}/attempt/submit", quiz).header("Authorization", student))
        .andExpect(status().isOk());
    MutableClockConfig.CLOCK.advance(Duration.ofDays(3));
    review(student, quiz, 200);

    String classmate = bearer(student(course, "qzrevmate"));
    assertThat(review(classmate, quiz, 404).path("code").asText()).isEqualTo("ATTEMPT_NOT_FOUND");
    String outsider = bearer(student(newCourse(), "qzrevout"));
    assertThat(review(outsider, quiz, 404).path("code").asText()).isEqualTo("QUIZ_NOT_FOUND");
    mockMvc
        .perform(get("/api/quizzes/{id}/attempt/review", quiz))
        .andExpect(status().isUnauthorized());
  }
}
