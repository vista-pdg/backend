package com.vista.pdg.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.quiz.service.QuizScenarioFactory.Scenario;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.llm.def.LlmAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Pide al modelo las preguntas de un lote y descarta las que no cumplen el contrato.
 *
 * <p>La validación es la segunda línea de defensa de la calidad: el prompt pide opciones parejas y
 * sin comodines, pero un modelo no siempre obedece. Lo que aquí no pasa no llega al banco.
 */
@Component
@RequiredArgsConstructor
public class QuizQuestionWriter {
  static final int OPTIONS = 4;
  private static final int MIN_STATEMENT = 40;
  private static final int MAX_STATEMENT = 1200;
  private static final int MAX_OPTION = 320;
  private static final List<String> WILDCARDS =
      List.of(
          "todas las anteriores",
          "ninguna de las anteriores",
          "todas las opciones",
          "ninguna de las opciones",
          "todas son correctas",
          "ninguna es correcta");

  private final LlmAdapter llm;
  private final ObjectMapper mapper;

  public record Draft(
      Scenario scenario,
      String statement,
      List<String> options,
      int correctIndex,
      String explanation,
      String hint) {}

  public List<Draft> write(AlgorithmDescriptor topic, List<Scenario> scenarios) {
    return write(topic, scenarios, QuizGuidance.NONE);
  }

  public List<Draft> write(
      AlgorithmDescriptor topic, List<Scenario> scenarios, QuizGuidance guidance) {
    String raw = llm.completeJson(systemPrompt(), userPrompt(topic, scenarios, guidance));
    return parse(raw, scenarios);
  }

  /**
   * HU-36 · CA-4: reescribe una pregunta del banco según lo que pide el docente, sobre el mismo
   * escenario y con las mismas validaciones. Vacío si el modelo no propone una versión válida.
   */
  public java.util.Optional<Draft> revise(
      AlgorithmDescriptor topic,
      Scenario scenario,
      Draft current,
      String changes,
      QuizGuidance guidance) {
    Map<String, Object> body = body(topic, List.of(scenario), guidance);
    Map<String, Object> question = new LinkedHashMap<>();
    question.put("statement", current.statement());
    question.put("options", current.options());
    question.put("correctIndex", current.correctIndex());
    question.put("explanation", current.explanation());
    question.put("hint", current.hint());
    body.put("pregunta_actual", question);
    body.put("cambios_pedidos", changes.strip());
    String raw =
        llm.completeJson(
            systemPrompt() + "\n\n" + resource("prompts/quiz/revision-prompt.txt"), json(body));
    return parse(raw, List.of(scenario)).stream().findFirst();
  }

  String userPrompt(AlgorithmDescriptor topic, List<Scenario> scenarios) {
    return userPrompt(topic, scenarios, QuizGuidance.NONE);
  }

  String userPrompt(AlgorithmDescriptor topic, List<Scenario> scenarios, QuizGuidance guidance) {
    return json(body(topic, scenarios, guidance));
  }

  private Map<String, Object> body(
      AlgorithmDescriptor topic, List<Scenario> scenarios, QuizGuidance guidance) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tema", QuizTopics.label(topic));
    body.put("descripcion", topic.description());
    body.put(
        "escenarios",
        scenarios.stream()
            .map(
                s -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("id", s.id());
                  item.put("escenario", s.description());
                  item.put("hechos", s.facts());
                  return item;
                })
            .toList());
    if (guidance.instructions() != null) body.put("instrucciones_docente", guidance.instructions());
    if (!guidance.excerpts().isEmpty()) {
      body.put(
          "material_de_referencia",
          guidance.excerpts().stream()
              .map(
                  e -> {
                    Map<String, Object> excerpt = new LinkedHashMap<>();
                    excerpt.put("fuente", e.source());
                    excerpt.put("extracto", e.text());
                    return excerpt;
                  })
              .toList());
    }
    return body;
  }

  private String json(Map<String, Object> body) {
    try {
      return mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo construir el prompt del quiz", e);
    }
  }

  List<Draft> parse(String raw, List<Scenario> scenarios) {
    JsonNode questions;
    try {
      questions = mapper.readTree(unwrap(raw)).path("questions");
    } catch (IOException e) {
      return List.of();
    }
    if (!questions.isArray()) return List.of();
    Map<Integer, Scenario> byId = new LinkedHashMap<>();
    scenarios.forEach(s -> byId.put(s.id(), s));
    List<Draft> drafts = new ArrayList<>();
    for (JsonNode node : questions) {
      Scenario scenario = byId.remove(node.path("scenarioId").asInt(-1));
      if (scenario == null) continue; // escenario inventado o repetido
      Draft draft = draft(node, scenario);
      if (draft != null) drafts.add(draft);
    }
    return drafts;
  }

  private static Draft draft(JsonNode node, Scenario scenario) {
    String statement = node.path("statement").asText("").strip();
    JsonNode rawOptions = node.path("options");
    if (statement.length() < MIN_STATEMENT || statement.length() > MAX_STATEMENT) return null;
    if (!rawOptions.isArray() || rawOptions.size() != OPTIONS) return null;
    if (!node.path("correctIndex").isInt()) return null;
    int correct = node.path("correctIndex").asInt();
    if (correct < 0 || correct >= OPTIONS) return null;

    List<String> options = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int shortest = Integer.MAX_VALUE;
    int longest = 0;
    for (JsonNode option : rawOptions) {
      String text = option.asText("").strip();
      String normalized = normalize(text);
      if (text.isEmpty() || text.length() > MAX_OPTION || !seen.add(normalized)) return null;
      if (isWildcard(normalized)) return null;
      shortest = Math.min(shortest, text.length());
      longest = Math.max(longest, text.length());
      options.add(text);
    }
    // Una opción mucho más larga que las demás delata la respuesta sin saber del tema.
    if (longest > shortest * 3 + 24) return null;
    if (options.get(correct).length() == longest && longest > secondLongest(options) * 2)
      return null;
    String explanation = node.path("explanation").asText("").strip();
    String hint = node.path("hint").asText("").strip();
    return new Draft(scenario, statement, List.copyOf(options), correct, explanation, hint);
  }

  /** Comodines que pierden sentido cuando las opciones se barajan por estudiante. */
  static boolean isWildcard(String normalized) {
    return WILDCARDS.stream().anyMatch(normalized::contains);
  }

  private static int secondLongest(List<String> options) {
    return options.stream()
        .mapToInt(String::length)
        .sorted()
        .skip(OPTIONS - 2L)
        .findFirst()
        .orElse(0);
  }

  static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .strip();
  }

  private static String unwrap(String raw) {
    if (raw == null) return "{}";
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    return start >= 0 && end > start ? raw.substring(start, end + 1) : raw;
  }

  private static String systemPrompt() {
    return resource("prompts/quiz/system-prompt.txt");
  }

  private static String resource(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo cargar el prompt de quizzes", e);
    }
  }
}
