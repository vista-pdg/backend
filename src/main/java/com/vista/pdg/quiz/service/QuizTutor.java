package com.vista.pdg.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.quiz.dto.StudyQuizDtos.TutorTurn;
import com.vista.pdg.quiz.entity.QuestionContent;
import com.vista.pdg.service.llm.def.LlmAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Tutor IA de una pregunta de estudio (HU-35 · CA-7). El modelo recibe la pregunta completa —con la
 * respuesta y los hechos verificados, para no equivocarse— y la regla de no revelarla mientras la
 * pregunta siga abierta. Como un modelo no siempre obedece, la respuesta pasa además por un filtro
 * determinista: si cita la opción correcta antes de tiempo, se sustituye por una orientación.
 */
@Component
@RequiredArgsConstructor
public class QuizTutor {
  static final int MAX_REPLY = 1500;

  private final LlmAdapter llm;
  private final ObjectMapper mapper;

  public String reply(
      QuestionContent question,
      boolean resolved,
      Integer selectedOption,
      String message,
      List<TutorTurn> history) {
    String raw;
    try {
      raw =
          llm.completeJson(
              systemPrompt(), userPrompt(question, resolved, selectedOption, message, history));
    } catch (LlmUnavailableException | LlmExhaustedException | UnsupportedOperationException e) {
      throw unavailable();
    }
    String reply = parse(raw);
    if (reply.isBlank()) throw unavailable();
    if (!resolved && reveals(reply, question)) {
      return "No puedo confirmarte la respuesta mientras la pregunta siga abierta. "
          + question.getHint();
    }
    return reply.length() <= MAX_REPLY ? reply : reply.substring(0, MAX_REPLY) + "…";
  }

  String userPrompt(
      QuestionContent q,
      boolean resolved,
      Integer selectedOption,
      String message,
      List<TutorTurn> history) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tema", q.getTopicLabel());
    body.put("escenario", q.getScenario());
    body.put("enunciado", q.getStatement());
    body.put("opciones", q.getOptions());
    body.put("hechos", q.getFacts() == null ? List.of() : q.getFacts());
    body.put(
        "respuesta_correcta",
        Map.of("indice", q.getCorrectIndex(), "texto", q.getOptions().get(q.getCorrectIndex())));
    body.put("justificacion", q.getExplanation() == null ? "" : q.getExplanation());
    body.put("resuelta", resolved);
    body.put(
        "respuesta_del_estudiante",
        selectedOption == null ? null : q.getOptions().get(selectedOption));
    body.put(
        "historial",
        (history == null ? List.<TutorTurn>of() : history)
            .stream()
                .map(
                    t ->
                        Map.of(
                            "rol",
                            "tutor".equals(t.role()) ? "tutor" : "estudiante",
                            "texto",
                            t.text()))
                .toList());
    body.put("pregunta_estudiante", message.strip());
    try {
      return mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo construir el prompt del tutor", e);
    }
  }

  private String parse(String raw) {
    if (raw == null) return "";
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    try {
      JsonNode node =
          mapper.readTree(start >= 0 && end > start ? raw.substring(start, end + 1) : raw);
      return node.path("reply").asText("").strip();
    } catch (IOException e) {
      return "";
    }
  }

  /**
   * La respuesta copia el texto de la opción correcta: con la pregunta abierta, eso es revelarla.
   */
  static boolean reveals(String reply, QuestionContent q) {
    String correct = QuizQuestionWriter.normalize(q.getOptions().get(q.getCorrectIndex()));
    return correct.length() >= 12 && QuizQuestionWriter.normalize(reply).contains(correct);
  }

  private static QuizException unavailable() {
    return new QuizException(
        503, "TUTOR_UNAVAILABLE", "El tutor no respondió. Intenta de nuevo.", null);
  }

  private static String systemPrompt() {
    try {
      return new ClassPathResource("prompts/quiz/tutor-prompt.txt")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo cargar el prompt del tutor", e);
    }
  }
}
