package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.quiz.service.QuizQuestionWriter;
import com.vista.pdg.quiz.service.QuizQuestionWriter.Draft;
import com.vista.pdg.quiz.service.QuizScenarioFactory.Scenario;
import com.vista.pdg.quiz.service.QuizStubResponses;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.llm.def.LlmAdapter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * HU-34 · CA-2: lo que el modelo devuelva mal formado o delatando la respuesta no llega al banco.
 */
class QuizQuestionWriterTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AlgorithmDescriptor TOPIC =
      new AlgorithmDescriptor(
          "tree",
          "avl",
          "insert",
          "tree",
          "AVL · Inserción",
          "Inserciones con rotaciones",
          "values");
  private static final List<Scenario> SCENARIOS =
      List.of(
          new Scenario(1, "Valores: [3, 2, 1]", List.of("Rotación simple a la derecha"), null),
          new Scenario(2, "Valores: [1, 2, 3]", List.of(), null));
  private static final String STATEMENT =
      "¿Qué explica la rotación que se observa al terminar las inserciones del escenario?";

  private final AtomicReference<String> response = new AtomicReference<>();
  private final AtomicReference<String> lastPrompt = new AtomicReference<>();
  private final QuizQuestionWriter writer =
      new QuizQuestionWriter(
          new LlmAdapter() {
            @Override
            public StructureContract generate(String userPrompt) {
              throw new UnsupportedOperationException();
            }

            @Override
            public String completeJson(String systemPrompt, String userPrompt) {
              assertThat(systemPrompt).contains("REGLAS DE LAS OPCIONES");
              lastPrompt.set(userPrompt);
              return response.get() == null
                  ? QuizStubResponses.forPrompt(userPrompt)
                  : response.get();
            }
          },
          MAPPER);

  private static ObjectNode question(
      int scenario, String statement, int correct, String... options) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("scenarioId", scenario);
    node.put("statement", statement);
    ArrayNode array = node.putArray("options");
    for (String option : options) array.add(option);
    node.put("correctIndex", correct);
    node.put("explanation", "porque sí");
    return node;
  }

  private List<Draft> write(ObjectNode... questions) {
    ObjectNode root = MAPPER.createObjectNode();
    ArrayNode array = root.putArray("questions");
    for (ObjectNode q : questions) array.add(q);
    response.set("```json\n" + root + "\n```");
    return writer.write(TOPIC, SCENARIOS);
  }

  @Test
  void sendsScenariosWithVerifiedFactsAndAcceptsWellFormedQuestions() throws Exception {
    List<Draft> drafts = writer.write(TOPIC, SCENARIOS);

    var prompt = MAPPER.readTree(lastPrompt.get());
    assertThat(prompt.path("tema").asText()).isEqualTo("Árbol AVL — AVL · Inserción");
    assertThat(prompt.path("escenarios")).hasSize(2);
    assertThat(prompt.path("escenarios").get(0).path("hechos").get(0).asText())
        .isEqualTo("Rotación simple a la derecha");
    assertThat(drafts).hasSize(2);
    assertThat(drafts.get(0).scenario().id()).isEqualTo(1);
    assertThat(drafts.get(0).options()).hasSize(4).doesNotHaveDuplicates();
    assertThat(drafts.get(0).correctIndex()).isEqualTo(QuizStubResponses.correctIndex(1));
    assertThat(drafts.get(1).correctIndex()).isEqualTo(2);
  }

  @Test
  void discardsQuestionsThatBreakTheContract() {
    String[] ok = {
      "Opción uno del caso", "Opción dos del caso", "Opción tres del caso", "Opción cuatro"
    };
    assertThat(write(question(1, STATEMENT, 0, ok))).hasSize(1);
    assertThat(write(question(1, "¿Cuál sale?", 0, ok))).as("enunciado trivial").isEmpty();
    assertThat(write(question(1, STATEMENT, 0, "a", "b", "c"))).as("tres opciones").isEmpty();
    assertThat(write(question(1, STATEMENT, 4, ok))).as("índice fuera de rango").isEmpty();
    assertThat(write(question(1, STATEMENT, 0, "Igual", "igual.", "Otra", "Distinta")))
        .as("opciones repetidas")
        .isEmpty();
    assertThat(write(question(1, STATEMENT, 0, "Rota a la derecha", "", "Rota doble", "No rota")))
        .as("opción vacía")
        .isEmpty();
    assertThat(
            write(
                question(
                    1,
                    STATEMENT,
                    0,
                    "Rota a la derecha",
                    "Rota a la izquierda",
                    "No rota",
                    "Todas las anteriores")))
        .as("comodín")
        .isEmpty();
    assertThat(write(question(7, STATEMENT, 0, ok))).as("escenario inventado").isEmpty();
    assertThat(write(question(1, STATEMENT, 0, ok), question(1, STATEMENT, 1, ok)))
        .as("dos preguntas para el mismo escenario")
        .hasSize(1);
    ObjectNode textIndex = question(1, STATEMENT, 0, ok);
    textIndex.put("correctIndex", "primera");
    assertThat(write(textIndex)).as("índice no numérico").isEmpty();
  }

  @Test
  void discardsOptionsWhoseLengthGivesTheAnswerAway() {
    String longAnswer =
        "Provoca una rotación simple a la derecha con pivote en el nodo desbalanceado porque el factor"
            + " de equilibrio pasa a +2 y el hijo izquierdo queda con +1, restaurando la altura previa";
    assertThat(
            write(
                question(
                    1, STATEMENT, 0, longAnswer, "No rota", "Rota doble", "Rota a la izquierda")))
        .as("la correcta es la única larga")
        .isEmpty();
    assertThat(
            write(
                question(
                    2,
                    STATEMENT,
                    1,
                    "Rotación doble izquierda-derecha con pivote en 29",
                    "Rotación simple a la derecha con pivote en 29",
                    "Rotación doble derecha-izquierda con pivote en 20",
                    "Ningún nodo queda desbalanceado tras insertar")))
        .hasSize(1);
  }

  @Test
  void anUnreadableResponseYieldsNoQuestions() {
    response.set("no puedo ayudar con eso");
    assertThat(writer.write(TOPIC, SCENARIOS)).isEmpty();
    response.set("{\"questions\": \"ninguna\"}");
    assertThat(writer.write(TOPIC, SCENARIOS)).isEmpty();
    response.set("{\"questions\": [");
    assertThat(writer.write(TOPIC, SCENARIOS)).isEmpty();
    assertThat(QuizStubResponses.forPrompt("esto no es JSON")).isEqualTo("{\"questions\":[]}");
  }
}
