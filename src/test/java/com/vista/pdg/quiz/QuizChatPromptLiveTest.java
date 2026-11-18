package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.assistant.service.AssistantScopeService;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.config.GeminiProperties;
import com.vista.pdg.controller.dto.GenerateRequest;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.quiz.entity.VistaSetup;
import com.vista.pdg.quiz.service.QuizScenarioCheck;
import com.vista.pdg.quiz.service.QuizScenarioFactory;
import com.vista.pdg.quiz.service.QuizTopics;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.llm.def.ConversationContext;
import com.vista.pdg.service.llm.impl.GeminiLlmAdapter;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HU-35 · CA-2, prueba viva y opcional: cada instrucción que un quiz inserta en el chat, enviada al
 * modelo real por la misma ruta que {@code POST /api/generate}, produce la estructura del
 * escenario.
 *
 * <p>Gasta cuota del proveedor, así que solo corre con {@code VISTA_LIVE_LLM=1} y {@code
 * GEMINI_API_KEY}. El contrato esperado se reconstruye leyendo la propia instrucción: además de
 * medir al modelo, comprueba que el texto lleva todos los datos del escenario. El informe queda en
 * {@code target/quiz-chat-live.tsv}.
 */
@EnabledIfEnvironmentVariable(named = "VISTA_LIVE_LLM", matches = "1")
class QuizChatPromptLiveTest extends IntegrationTestSupport {
  private static final Pattern VALUES =
      Pattern.compile("valores(?:, en este orden,)? (?:en este orden )?([\\d, ]+)");
  private static final Pattern EDGE = Pattern.compile("([A-F])-([A-F]) peso (\\d+)");
  private static final Pattern BUCKETS = Pattern.compile("tabla hash de (\\d+) cubetas");

  @Autowired private QuizScenarioFactory factory;
  @Autowired private QuizScenarioCheck check;
  @Autowired private QuizTopics topics;
  @Autowired private ContractBuilder contracts;
  @Autowired private AssistantScopeService scope;
  @Autowired private GeneratorDispatcher generators;
  @Autowired private ObjectMapper mapper;

  @Test
  void everyChatInstructionBuildsTheScenarioWithTheRealModel() throws Exception {
    String key = System.getenv("GEMINI_API_KEY");
    String model = System.getenv().getOrDefault("GEMINI_API_MODEL", "gemini-3.5-flash-lite");
    assertThat(key).as("GEMINI_API_KEY").isNotBlank();
    GeminiLlmAdapter gemini =
        new GeminiLlmAdapter(
            new GeminiProperties(new GeminiProperties.Api(key, model, false, null, null)),
            contracts);
    try {
      List<String> report = new ArrayList<>(List.of("tema\tcontexto\tresultado\tdetalle"));
      int failures = 0;
      for (var topic : topics.all()) {
        var descriptor = topics.find(topic.key()).orElseThrow();
        VistaSetup setup = factory.build(descriptor, new Random(21), 1).orElseThrow().setup();
        if (setup.chatPrompt() == null) {
          report.add(topic.key() + "\t-\tsin chat\tse reproduce con valores");
          continue;
        }
        String outcome = run(gemini, setup, ConversationContext.empty());
        failures += outcome.startsWith("OK") ? 0 : 1;
        report.add(topic.key() + "\tvacío\t" + outcome);
      }
      // «Desde cero» con una sesión de la misma familia: no debe refinar la estructura vigente.
      for (String key2 : List.of("tree/avl/insert", "graph/simple/bfs", "stack/simple/peek")) {
        var descriptor = topics.find(key2).orElseThrow();
        VistaSetup setup = factory.build(descriptor, new Random(22), 1).orElseThrow().setup();
        ConversationContext context = previousSession(setup);
        String outcome = run(gemini, setup, context);
        failures += outcome.startsWith("OK") ? 0 : 1;
        report.add(key2 + "\tsesión previa\t" + outcome);
      }
      Path out = Path.of("target", "quiz-chat-live.tsv");
      Files.writeString(out, String.join("\n", report) + "\n");
      System.out.println(String.join("\n", report));
      assertThat(failures).as("instrucciones que el modelo no reprodujo; ver " + out).isZero();
    } finally {
      gemini.close();
    }
  }

  private String run(GeminiLlmAdapter gemini, VistaSetup setup, ConversationContext context) {
    StructureContract expected = expectedFrom(setup);
    StructureResponse wanted =
        check
            .structure(
                setup.chatPrompt(), setup.structureType(), setup.structureSubtype(), expected)
            .orElseThrow();
    GenerateRequest request =
        new GenerateRequest(setup.chatPrompt(), setup.structureType(), setup.structureSubtype());
    try {
      scope.validate(request, context);
      StructureContract got = generateWithPace(gemini, scope.modelPrompt(request), context);
      scope.verifyResult(request, got);
      GeneratedStructure built = generators.dispatch(got);
      String want = shape(wanted.nodes(), wanted.edges());
      String have = shape(built.nodes(), built.edges());
      return want.equals(have)
          ? "OK\t" + built.nodes().size() + " nodos"
          : "DISTINTO\tesperado " + want + " · obtenido " + have;
    } catch (RuntimeException e) {
      return "FALLO\t" + e.getClass().getSimpleName() + ": " + e.getMessage();
    }
  }

  /**
   * Los planes gratuitos limitan las peticiones por minuto: se espera entre llamadas y, ante un 429
   * del proveedor, se reintenta pasado el minuto. Un 429 no dice nada de la instrucción.
   */
  private static StructureContract generateWithPace(
      GeminiLlmAdapter gemini, String prompt, ConversationContext context) {
    long pace = Long.parseLong(System.getenv().getOrDefault("VISTA_LIVE_LLM_PACE_MS", "5000"));
    for (int attempt = 1; ; attempt++) {
      sleep(pace);
      try {
        return gemini.generate(prompt, context);
      } catch (com.vista.pdg.exception.LlmUnavailableException e) {
        if (attempt >= 3 || e.getMessage() == null || !e.getMessage().contains("429")) throw e;
        sleep(65_000);
      }
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /**
   * Una sesión con otra estructura de la misma familia, como la de un estudiante que ya trabajaba.
   */
  private ConversationContext previousSession(VistaSetup setup) throws Exception {
    StructureContract other =
        switch (setup.structureType()) {
          case "tree" ->
              new TreeContract(
                  "tree",
                  null,
                  setup.structureSubtype(),
                  List.of(new TreeContract.Operation("insert", List.of(3, 1, 2))),
                  null);
          case "stack" -> new StackContract("stack", null, List.of(7, 8, 9));
          default ->
              new GraphContract(
                  "graph",
                  null,
                  false,
                  false,
                  List.of("A", "B", "C"),
                  new GraphContract.MatrixDef(
                      "adjacency",
                      List.of(List.of(0, 1, 0), List.of(1, 0, 1), List.of(0, 1, 0)),
                      null));
        };
    return new ConversationContext(
        mapper.writeValueAsString(other), List.of("usuario: una estructura de prueba"));
  }

  /** El contrato que la instrucción describe, leído solo de su texto. */
  private static StructureContract expectedFrom(VistaSetup setup) {
    String prompt = setup.chatPrompt();
    String type = setup.structureType();
    if ("graph".equals(type)) {
      Matcher m = EDGE.matcher(prompt);
      TreeSet<String> labels = new TreeSet<>(List.of("A", "B", "C", "D", "E", "F"));
      List<String> order = new ArrayList<>(labels);
      List<List<Integer>> matrix = new ArrayList<>();
      for (int i = 0; i < order.size(); i++)
        matrix.add(new ArrayList<>(Collections.nCopies(order.size(), 0)));
      while (m.find()) {
        int a = order.indexOf(m.group(1));
        int b = order.indexOf(m.group(2));
        matrix.get(a).set(b, Integer.parseInt(m.group(3)));
        matrix.get(b).set(a, Integer.parseInt(m.group(3)));
      }
      return new GraphContract(
          "graph",
          null,
          false,
          true,
          order,
          new GraphContract.MatrixDef("adjacency", matrix, null));
    }
    Matcher v = VALUES.matcher(prompt);
    assertThat(v.find()).as("valores en: " + prompt).isTrue();
    List<Integer> values =
        Arrays.stream(v.group(1).split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .map(Integer::valueOf)
            .toList();
    return switch (type) {
      case "tree" ->
          new TreeContract(
              "tree",
              null,
              setup.structureSubtype(),
              List.of(new TreeContract.Operation("insert", values)),
              null);
      case "stack" -> new StackContract("stack", null, values);
      case "queue" -> new QueueContract("queue", null, values);
      case "linked-list" -> new LinkedListContract("linked-list", null, "singly", values);
      case "hash-table" -> {
        Matcher b = BUCKETS.matcher(prompt);
        assertThat(b.find()).as("cubetas en: " + prompt).isTrue();
        yield new HashTableContract(
            "hash-table", null, Integer.parseInt(b.group(1)), values, "modular");
      }
      default -> throw new IllegalStateException("Tipo sin contrato esperado: " + type);
    };
  }

  /** Etiquetas y aristas dirigidas entre etiquetas: no depende de ids ni de posiciones. */
  private static String shape(List<Node3D> nodes, List<Edge3D> edges) {
    Map<String, String> label = new HashMap<>();
    nodes.forEach(n -> label.put(n.id(), n.label()));
    List<String> labels = nodes.stream().map(Node3D::label).sorted().toList();
    List<String> links =
        edges.stream()
            .map(
                e ->
                    label.get(e.from())
                        + ">"
                        + label.get(e.to())
                        + (e.weight() == null ? "" : ":" + e.weight()))
            .sorted()
            .toList();
    return labels + "|" + links;
  }
}
