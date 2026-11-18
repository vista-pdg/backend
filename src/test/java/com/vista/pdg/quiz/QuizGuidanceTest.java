package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.quiz.repository.QuizAttemptItemRepository;
import com.vista.pdg.quiz.service.QuizStubResponses;
import com.vista.pdg.testsupport.FakeLlmConfig;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** HU-36 · CA-1 a CA-4 y CA-9: instrucciones, material, edición del banco y tiempo por pregunta. */
class QuizGuidanceTest extends QuizTestSupport {
  static final String AVL_NOTES =
      "# Árboles AVL\n\nEn el curso, un árbol AVL guarda el factor de balance de cada nodo. Tras"
          + " una inserción se recalcula el factor de balance de los ancestros y una rotación"
          + " simple o doble lo devuelve al rango [-1, 1].\n\n# Otro tema\n\nLas matrices"
          + " dispersas no se evalúan en este quiz.";

  @Autowired private QuizAttemptItemRepository items;

  private ResultActions upload(String token, long quiz, String name, String type, byte[] bytes)
      throws Exception {
    return mockMvc.perform(
        multipart("/api/quizzes/manage/{id}/materials/file", quiz)
            .file(new MockMultipartFile("file", name, type, bytes))
            .header("Authorization", token));
  }

  private ResultActions link(String token, long quiz, String url) throws Exception {
    return mockMvc.perform(
        authed(post("/api/quizzes/manage/{id}/materials/link", quiz), token)
            .content(json(Map.of("url", url, "crawl", false))));
  }

  private static byte[] docx(String text) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write(
          ("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                  + "<w:body><w:p><w:r><w:t>"
                  + text
                  + "</w:t></w:r></w:p></w:body></w:document>")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  private Map<String, Object> edit(String statement, List<String> options, int correct) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("statement", statement);
    body.put("options", options);
    body.put("correctIndex", correct);
    body.put("explanation", "Justificación escrita por el docente.");
    body.put("hint", "Mira el factor de balance.");
    body.put("source", "MANUAL");
    return body;
  }

  @Test
  void instructionsAndRelatedMaterialReachEveryBatchOfTheirTopic() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    Map<String, Object> body = quizBody(course, 1, 4);
    body.put("instructions", "  Usa la notación del libro CyED1.  ");
    long quiz = createQuiz(teacher, body);

    upload(teacher, quiz, "avl.md", "text/markdown", AVL_NOTES.getBytes(StandardCharsets.UTF_8))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.format").value("MARKDOWN"))
        .andExpect(jsonPath("$.name").value("avl.md"))
        .andExpect(jsonPath("$.content").doesNotExist());
    upload(teacher, quiz, "hash.docx", "application/octet-stream", docx("Tablas hash y cubetas"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.format").value("DOCX"));
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}", quiz), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructions").value("Usa la notación del libro CyED1."))
        .andExpect(jsonPath("$.materials.length()").value(2));

    List<String> prompts = new ArrayList<>();
    FakeLlmConfig.QUIZ_RESPONDER.set(
        prompt -> {
          prompts.add(prompt);
          return QuizStubResponses.forPrompt(prompt);
        });
    fillBank(teacher, quiz);
    assertThat(prompts).isNotEmpty();
    assertThat(prompts).allMatch(p -> p.contains("Usa la notación del libro CyED1."));
    List<String> avl = prompts.stream().filter(p -> p.contains("AVL · Inserción")).toList();
    List<String> bfs = prompts.stream().filter(p -> p.contains("BFS")).toList();
    assertThat(avl).isNotEmpty().allMatch(p -> p.contains("material_de_referencia"));
    assertThat(avl).allMatch(p -> p.contains("factor de balance") && p.contains("avl.md"));
    assertThat(avl).noneMatch(p -> p.contains("cubetas") || p.contains("hash.docx"));
    assertThat(bfs).isNotEmpty().noneMatch(p -> p.contains("material_de_referencia"));
  }

  @Test
  void materialIsValidatedLimitedAndOnlyEditableInDraft() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    long quiz = createQuiz(teacher, quizBody(course, 1, 2));

    upload(teacher, quiz, "slides.pptx", "application/octet-stream", new byte[] {1, 2, 3})
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.fieldErrors.file").value(org.hamcrest.Matchers.containsString("formato")));
    upload(teacher, quiz, "vacio.md", "text/markdown", new byte[0])
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.file").exists());
    upload(teacher, quiz, "escaneo.pdf", "application/pdf", "no es un pdf".getBytes())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.file").exists());
    // La guardia de red es la real: una dirección local nunca se descarga.
    link(teacher, quiz, "http://127.0.0.1/notas.md")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.fieldErrors.url").value(org.hamcrest.Matchers.containsString("privada")));
    link(teacher, quiz, "ftp://servidor/notas.md")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.url").exists());

    long first = 0;
    for (int i = 0; i < 5; i++) {
      JsonNode saved =
          read(
              upload(teacher, quiz, "n" + i + ".txt", "text/plain", ("Pila " + i).getBytes())
                  .andExpect(status().isCreated()));
      if (i == 0) first = saved.path("id").asLong();
    }
    upload(teacher, quiz, "sexta.txt", "text/plain", "Pila 6".getBytes())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MATERIAL_LIMIT"));
    mockMvc
        .perform(
            delete("/api/quizzes/manage/{id}/materials/{m}", quiz, first)
                .header("Authorization", teacher))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{id}/guidance", quiz), teacher)
                .content(json(Map.of("instructions", "x".repeat(2001)))))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{id}/guidance", quiz), teacher)
                .content(json(Map.of("instructions", "Enfócate en invariantes."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructions").value("Enfócate en invariantes."))
        .andExpect(jsonPath("$.materials.length()").value(4));

    // Otro curso no asignado: 403; un estudiante: 403.
    String other = newCourse();
    long foreign = createQuiz(admin(), quizBody(other, 1, 2));
    upload(teacher, foreign, "a.md", "text/markdown", "# A".getBytes())
        .andExpect(status().isForbidden());
    String student = bearer(student(course, "hu36m"));
    upload(student, quiz, "a.md", "text/markdown", "# A".getBytes())
        .andExpect(status().isForbidden());

    // Publicado: instrucciones y material en solo lectura.
    fillBank(teacher, quiz);
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", quiz), teacher))
        .andExpect(status().isOk());
    upload(teacher, quiz, "tarde.md", "text/markdown", "# Tarde".getBytes())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUIZ_ALREADY_PUBLISHED"));
    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{id}/guidance", quiz), teacher)
                .content(json(Map.of("instructions", "otra"))))
        .andExpect(status().isConflict());
  }

  @Test
  void questionsAreEditedByHandWithFieldValidation() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    long quiz = createQuiz(teacher, quizBody(course, 1, 2));
    fillBank(teacher, quiz);
    JsonNode question = bank(teacher, quiz).get(0);
    long id = question.path("id").asLong();
    assertThat(question.path("editable").asBoolean()).isTrue();
    assertThat(question.path("hint").asText()).isNotBlank();
    String url = "/api/quizzes/manage/{q}/questions/{id}";
    List<String> options =
        List.of("Rota a la derecha", "Rota a la izquierda", "No rota", "Rota dos veces");

    mockMvc
        .perform(authed(put(url, quiz, id), teacher).content(json(edit("¿Qué pasa?", options, 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.statement").exists());
    mockMvc
        .perform(
            authed(put(url, quiz, id), teacher)
                .content(
                    json(
                        edit(
                            "¿Qué rotación ocurre al insertar 23 en el escenario?",
                            List.of("Rota", "No rota", "Doble", "rota"),
                            0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['options.3']").exists());
    mockMvc
        .perform(
            authed(put(url, quiz, id), teacher)
                .content(
                    json(
                        edit(
                            "¿Qué rotación ocurre al insertar 23 en el escenario?",
                            List.of("Rota", "No rota", "Doble", "Ninguna de las anteriores"),
                            0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['options.3']").exists());
    Map<String, Object> longHint =
        edit("¿Qué rotación ocurre al insertar 23 en el escenario?", options, 1);
    longHint.put("hint", "x".repeat(201));
    mockMvc
        .perform(authed(put(url, quiz, id), teacher).content(json(longHint)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.hint").exists());

    mockMvc
        .perform(
            authed(put(url, quiz, id), teacher)
                .content(
                    json(
                        edit(
                            "  ¿Qué rotación ocurre al insertar 23 en el escenario?  ",
                            options,
                            2))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.statement").value("¿Qué rotación ocurre al insertar 23 en el escenario?"))
        .andExpect(jsonPath("$.correctIndex").value(2))
        .andExpect(jsonPath("$.editKind").value("MANUAL"))
        .andExpect(jsonPath("$.editedAt").exists())
        .andExpect(jsonPath("$.scenario").value(question.path("scenario").asText()));
    JsonNode after = bank(teacher, quiz).get(0);
    assertThat(after.path("options").get(2).asText()).isEqualTo("No rota");
    assertThat(after.path("hint").asText()).isEqualTo("Mira el factor de balance.");

    String student = bearer(student(course, "hu36e"));
    mockMvc
        .perform(
            authed(put(url, quiz, id), student).content(json(edit("x".repeat(30), options, 0))))
        .andExpect(status().isForbidden());
  }

  @Test
  void aiProposesWithoutSavingAndAppliedProposalIsMarked() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    Map<String, Object> body = quizBody(course, 1, 2);
    body.put("instructions", "Pregunta por invariantes.");
    long quiz = createQuiz(teacher, body);
    fillBank(teacher, quiz);
    JsonNode question = bank(teacher, quiz).get(0);
    long id = question.path("id").asLong();
    String url = "/api/quizzes/manage/{q}/questions/{id}/ai-proposal";

    List<String> prompts = new ArrayList<>();
    FakeLlmConfig.QUIZ_RESPONDER.set(
        prompt -> {
          prompts.add(prompt);
          return QuizStubResponses.forPrompt(prompt);
        });
    JsonNode proposal =
        read(
            mockMvc
                .perform(
                    authed(post(url, quiz, id), teacher)
                        .content(json(Map.of("instruction", "Distractores más sutiles"))))
                .andExpect(status().isOk()));
    assertThat(proposal.path("statement").asText())
        .startsWith("Versión revisada: " + question.path("statement").asText());
    assertThat(proposal.path("options")).hasSize(4);
    assertThat(prompts.getLast())
        .contains("cambios_pedidos", "Distractores más sutiles", "pregunta_actual")
        .contains("Pregunta por invariantes.", question.path("scenario").asText());
    // La propuesta no se guarda sola.
    assertThat(bank(teacher, quiz).get(0).path("statement").asText())
        .isEqualTo(question.path("statement").asText());

    Map<String, Object> apply = new LinkedHashMap<>();
    apply.put("statement", proposal.path("statement").asText());
    apply.put("options", objectMapper.convertValue(proposal.path("options"), List.class));
    apply.put("correctIndex", proposal.path("correctIndex").asInt());
    apply.put("explanation", proposal.path("explanation").asText());
    apply.put("hint", proposal.path("hint").asText());
    apply.put("source", "AI");
    mockMvc
        .perform(
            authed(put("/api/quizzes/manage/{q}/questions/{id}", quiz, id), teacher)
                .content(json(apply)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editKind").value("AI"));

    FakeLlmConfig.QUIZ_RESPONDER.set(prompt -> "{\"questions\":[]}");
    mockMvc
        .perform(
            authed(post(url, quiz, id), teacher).content(json(Map.of("instruction", "Otra vez"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("AI_PROPOSAL_INVALID"));
    mockMvc
        .perform(authed(post(url, quiz, id), teacher).content(json(Map.of("instruction", " "))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.instruction").exists());
  }

  @Test
  void publishedQuizOnlyEditsUnassignedQuestionsAndAnswersRecordTime() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    String teacher = teacher();
    String student = bearer(student(course, "hu36t"));
    long quiz = publishedQuiz(teacher, quizBody(course, 1, 3));
    JsonNode attempt = start(student, quiz);
    assertThat(attempt.path("questions")).hasSize(1);

    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("selectedOption", 0);
    answer.put("elapsedMs", 999_999_999L);
    mockMvc
        .perform(
            authed(put("/api/quizzes/{id}/attempt/answers/{p}", quiz, 1), student)
                .content(json(answer)))
        .andExpect(status().isOk());
    var item =
        items.findByAttempt_Quiz_IdOrderByAttempt_IdAscPositionAsc(quiz).stream()
            .findFirst()
            .orElseThrow();
    // El cliente no puede declarar más tiempo del que lleva el intento.
    assertThat(item.getTimeSpentMs()).isNotNull().isLessThan(999_999_999);
    answer.put("elapsedMs", -5);
    mockMvc
        .perform(
            authed(put("/api/quizzes/{id}/attempt/answers/{p}", quiz, 1), student)
                .content(json(answer)))
        .andExpect(status().isUnprocessableEntity());

    List<String> options =
        List.of("Primera opción", "Segunda opción", "Tercera opción", "Cuarta opción");
    for (JsonNode q : bank(teacher, quiz)) {
      boolean isAssigned = q.path("timesAssigned").asInt() > 0;
      assertThat(q.path("editable").asBoolean()).isEqualTo(!isAssigned);
      var result =
          mockMvc.perform(
              authed(
                      put("/api/quizzes/manage/{q}/questions/{id}", quiz, q.path("id").asLong()),
                      teacher)
                  .content(
                      json(edit("Enunciado editado tras publicar el quiz del curso", options, 0))));
      if (isAssigned) {
        result
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("QUESTION_LOCKED"));
      } else {
        result.andExpect(status().isOk());
      }
    }
  }
}
