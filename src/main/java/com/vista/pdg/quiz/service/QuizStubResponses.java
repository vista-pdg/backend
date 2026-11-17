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
