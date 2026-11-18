package com.vista.pdg.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Banco determinista para el perfil {@code e2e} y las pruebas: una pregunta válida por escenario,
 * sin red ni cuota. La opción correcta es siempre {@code id % 4}, lo que permite a una prueba
 * responder bien o mal a voluntad.
 */
public final class QuizStubResponses {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String[] ENDINGS = {
    "conserva el invariante y no reestructura", "rompe el invariante y reestructura una vez",
    "conserva el invariante tras dos ajustes", "rompe el invariante y no lo restaura"
  };

  private QuizStubResponses() {}

  public static String forPrompt(String userPrompt) {
    try {
      JsonNode prompt = MAPPER.readTree(userPrompt);
      // HU-35: el tutor de estudio responde siempre lo mismo, citando el tema y la pregunta.
      if (prompt.has("pregunta_estudiante")) {
        return MAPPER
            .createObjectNode()
            .put(
                "reply",
                "Tutor de prueba sobre «"
                    + prompt.path("tema").asText("")
                    + "»: observa el escenario en VISTA. Preguntaste: "
                    + prompt.path("pregunta_estudiante").asText(""))
            .toString();
      }
      // HU-36: la revisión devuelve una versión marcada como revisada del mismo escenario.
      if (prompt.has("cambios_pedidos")) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode question = root.putArray("questions").addObject();
        int id = prompt.path("escenarios").path(0).path("id").asInt(1);
        question.put("scenarioId", id);
        question.put(
            "statement",
            "Versión revisada: "
                + prompt.path("pregunta_actual").path("statement").asText("")
                + " ("
                + prompt.path("cambios_pedidos").asText("")
                + ")");
        ArrayNode options = question.putArray("options");
        for (int i = 0; i < ENDINGS.length; i++) {
          options.add("Revisada " + (i + 1) + ": la operación " + ENDINGS[i]);
        }
        question.put("correctIndex", correctIndex(id));
        question.put("explanation", "Justificación revisada del sustituto de pruebas.");
        question.put("hint", "Pista revisada: observa el escenario en VISTA.");
        return root.toString();
      }
    } catch (Exception e) {
      // Prompt ilegible: se trata como el del banco.
    }
    ObjectNode root = MAPPER.createObjectNode();
    ArrayNode questions = root.putArray("questions");
    try {
      JsonNode prompt = MAPPER.readTree(userPrompt);
      String topic = prompt.path("tema").asText("VISTA");
      for (JsonNode scenario : prompt.path("escenarios")) {
        int id = scenario.path("id").asInt();
        ObjectNode question = questions.addObject();
        question.put("scenarioId", id);
        question.put(
            "statement",
            "Al ejecutar el escenario en VISTA para «"
                + topic
                + "», ¿qué afirmación explica el comportamiento observado? [E"
                + id
                + "]");
        ArrayNode options = question.putArray("options");
        for (int i = 0; i < ENDINGS.length; i++) {
          options.add(
              "Variante " + (i + 1) + ": la operación " + ENDINGS[(i + id) % ENDINGS.length]);
        }
        question.put("correctIndex", correctIndex(id));
        question.put("explanation", "Respuesta determinista del sustituto de pruebas.");
        question.put("hint", "Observa en VISTA el paso en que cambia la estructura. [E" + id + "]");
      }
    } catch (Exception e) {
      // Prompt ilegible: banco vacío, igual que un modelo que no respeta el formato.
    }
    return root.toString();
  }

  public static int correctIndex(int scenarioId) {
    return Math.floorMod(scenarioId, 4);
  }
}
