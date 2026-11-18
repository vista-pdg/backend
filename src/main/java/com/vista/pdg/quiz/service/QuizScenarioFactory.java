package com.vista.pdg.quiz.service;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.quiz.entity.VistaSetup;
import com.vista.pdg.service.algorithm.AlgorithmDispatcher;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sortea la instancia concreta de una pregunta, comprueba que VISTA la reproduce y la ejecuta con
 * el algoritmo real.
 *
 * <p>El modelo de lenguaje se equivoca al simular algoritmos; VISTA no. Por eso el escenario lo
 * decide el servidor y los «hechos» que acompañan al prompt salen del rastro verificado del {@link
 * AlgorithmDispatcher}. Desde HU-35, además, cada escenario trae la instrucción con la que el
 * estudiante lo construye en el chat: esa instrucción pasa por {@link QuizScenarioCheck} y el
 * algoritmo corre sobre la estructura resultante, que es la que el estudiante tendrá en el lienzo.
 * Un sorteo que no supera alguna etapa se descarta y se vuelve a sortear.
 */
@Component
@RequiredArgsConstructor
public class QuizScenarioFactory {
  static final int MAX_FACTS = 30;
  static final int MAX_FACT_LENGTH = 200;

  /** Sorteos por escenario antes de darlo por imposible. */
  static final int MAX_DRAWS = 6;

  private static final List<String> LABELS = List.of("A", "B", "C", "D", "E", "F");

  private final AlgorithmDispatcher dispatcher;
  private final QuizScenarioCheck check;

  public record Scenario(int id, String description, List<String> facts, VistaSetup setup) {}

  /** Instrucción del chat y contrato que describe, construidos con los mismos datos. */
  private record Chat(String prompt, String type, String subtype, StructureContract contract) {}

  /**
   * VISTA reproduce el tema si se ejecuta con valores en el panel de algoritmos o si el chat puede
   * construir la estructura sobre la que corre. El B-árbol no cumple ninguna de las dos.
   */
  public boolean supports(AlgorithmDescriptor topic) {
    return "values".equals(topic.input())
        || "graph".equals(topic.type())
        || chat(topic.type(), topic.subtype(), List.of(1, 2, 3), 5) != null;
  }

  public Optional<Scenario> build(AlgorithmDescriptor topic, Random random, int id) {
    if (!supports(topic)) return Optional.empty();
    for (int draw = 0; draw < MAX_DRAWS; draw++) {
      Optional<Scenario> scenario = draw(topic, random, id);
      if (scenario.isPresent()) return scenario;
    }
    return Optional.empty();
  }

  private Optional<Scenario> draw(AlgorithmDescriptor topic, Random random, int id) {
    if ("graph".equals(topic.type())) return graph(topic, random, id);
    int size = "sorting".equals(topic.family()) ? 6 : 7 + random.nextInt(2);
    List<Integer> values = distinctValues(random, size);
    if ("values".equals(topic.input())) return withValues(topic, values, id);
    return onCanvas(topic, random, id, values);
  }

  // ── Grafos ────────────────────────────────────────────────────────────

  private Optional<Scenario> graph(AlgorithmDescriptor topic, Random random, int id) {
    Set<String> used = new LinkedHashSet<>();
    List<Edge3D> edges = new ArrayList<>();
    // Árbol generador aleatorio: garantiza un grafo conexo antes de añadir aristas extra.
    List<String> order = new ArrayList<>(LABELS);
    Collections.shuffle(order, random);
    for (int i = 1; i < order.size(); i++) {
      addEdge(edges, used, order.get(random.nextInt(i)), order.get(i), random);
    }
    for (int guard = 0; edges.size() < 9 && guard < 60; guard++) {
      String a = LABELS.get(random.nextInt(LABELS.size()));
      String b = LABELS.get(random.nextInt(LABELS.size()));
      if (!a.equals(b)) addEdge(edges, used, a, b, random);
    }
    String description =
        "Grafo no dirigido y ponderado con vértices A, B, C, D, E y F. Aristas (peso): "
            + edges.stream()
                .map(e -> e.from() + "–" + e.to() + " (" + e.weight() + ")")
                .collect(Collectors.joining(", "))
            + ". Nodo inicial: A.";
    String prompt =
        "Construye desde cero un grafo no dirigido y ponderado con los vértices A, B, C, D, E y F"
            + " y estas aristas con su peso: "
            + edges.stream()
                .map(e -> e.from() + "-" + e.to() + " peso " + e.weight())
                .collect(Collectors.joining(", "))
            + ".";
    List<List<Integer>> matrix = new ArrayList<>();
    for (int i = 0; i < LABELS.size(); i++) {
      matrix.add(new ArrayList<>(Collections.nCopies(LABELS.size(), 0)));
    }
    for (Edge3D e : edges) {
      int a = LABELS.indexOf(e.from());
      int b = LABELS.indexOf(e.to());
      matrix.get(a).set(b, e.weight());
      matrix.get(b).set(a, e.weight());
    }
    GraphContract contract =
        new GraphContract(
            "graph",
            null,
            false,
            true,
            LABELS,
            new GraphContract.MatrixDef("adjacency", matrix, null));
    return onStructure(
        topic, id, description, new Chat(prompt, "graph", null, contract), null, "A");
  }

  private static void addEdge(
      List<Edge3D> edges, Set<String> used, String a, String b, Random random) {
    String key = a.compareTo(b) < 0 ? a + b : b + a;
    if (used.add(key)) {
      edges.add(new Edge3D("e" + edges.size(), a, b, 1 + random.nextInt(9), false));
    }
  }

  // ── Algoritmos que construyen su propia estructura con valores ────────

  /**
   * El estudiante lo reproduce cargando los valores en el panel de algoritmos. La instrucción del
   * chat solo se ofrece si el chat construye la misma estructura con la que el rastro empieza o
   * termina: una inserción AVL sí; un heapify no, porque el chat inserta uno a uno.
   */
  private Optional<Scenario> withValues(AlgorithmDescriptor topic, List<Integer> values, int id) {
    StepsResponse run = run(request(topic, values, null, null, null, null));
    if (run == null || run.steps() == null || run.steps().isEmpty()) return Optional.empty();
    Chat chat = chat(topic.type(), topic.subtype(), values, 0);
    Chat offered = null;
    if (chat != null) {
      Optional<StructureResponse> canvas =
          check.structure(chat.prompt(), chat.type(), chat.subtype(), chat.contract());
      if (canvas.isPresent()) {
        String shape = shape(canvas.get().nodes(), canvas.get().edges());
        AlgorithmStep first = run.steps().getFirst();
        AlgorithmStep last = run.steps().getLast();
        if (shape.equals(shape(first.nodes(), first.edges()))
            || shape.equals(shape(last.nodes(), last.edges()))) offered = chat;
      }
    }
    String description =
        QuizTopics.structureName(topic)
            + ": valores de entrada, en este orden: "
            + list(values)
            + ".";
    VistaSetup setup =
        new VistaSetup(
            offered == null ? null : offered.prompt(),
            offered == null ? null : offered.type(),
            offered == null ? null : offered.subtype(),
            topic.key(),
            values,
            null,
            null);
    return Optional.of(new Scenario(id, description, facts(run), setup));
  }

  // ── Operaciones sobre la estructura del lienzo ────────────────────────

  private Optional<Scenario> onCanvas(
      AlgorithmDescriptor topic, Random random, int id, List<Integer> values) {
    Integer argument = null;
    String operand = "";
    if (topic.parameter() != null) {
      boolean fresh = "value".equals(topic.parameter()) || random.nextInt(4) == 0;
      argument = fresh ? freshValue(random, values) : values.get(random.nextInt(values.size()));
      operand = " Valor de la operación: " + argument + ".";
    }
    int buckets = random.nextBoolean() ? 5 : 7;
    Chat chat = chat(topic.type(), topic.subtype(), values, buckets);
    if (chat == null) return Optional.empty();
    String description = built(topic, buckets) + list(values) + "." + operand;
    return onStructure(topic, id, description, chat, argument, null);
  }

  /** La estructura que deja el chat, y sobre ella el algoritmo, como lo haría el estudiante. */
  private Optional<Scenario> onStructure(
      AlgorithmDescriptor topic,
      int id,
      String description,
      Chat chat,
      Integer argument,
      String startLabel) {
    Optional<StructureResponse> canvas =
        check.structure(chat.prompt(), chat.type(), chat.subtype(), chat.contract());
    if (canvas.isEmpty()) return Optional.empty();
    List<Node3D> nodes = canvas.get().nodes();
    String start =
        startLabel == null
            ? null
            : nodes.stream()
                .filter(n -> startLabel.equals(n.label()))
                .map(Node3D::id)
                .findFirst()
                .orElse(null);
    if (startLabel != null && start == null) return Optional.empty();
    StepsResponse run = run(request(topic, null, nodes, canvas.get().edges(), start, argument));
    if (run == null || run.steps() == null || run.steps().isEmpty()) return Optional.empty();
    VistaSetup setup =
        new VistaSetup(
            chat.prompt(), chat.type(), chat.subtype(), topic.key(), null, argument, startLabel);
    return Optional.of(new Scenario(id, description, facts(run), setup));
  }

  /** «Árbol AVL construido con los valores, en este orden: » y sus variantes en femenino. */
  private static String built(AlgorithmDescriptor topic, int buckets) {
    return switch (topic.type()) {
      case "stack" ->
          "Pila (el último valor queda en el tope) construida con los valores, en este orden: ";
      case "queue" ->
          "Cola (el primer valor queda al frente) construida con los valores, en este orden: ";
      case "linked-list" -> "Lista enlazada simple construida con los valores, en este orden: ";
      case "hash-table" ->
          "Tabla hash de "
              + buckets
              + " cubetas (h(k) = k mod "
              + buckets
              + ", encadenamiento) construida con los valores, en este orden: ";
      default -> QuizTopics.structureName(topic) + " construido con los valores, en este orden: ";
    };
  }

  /**
   * Instrucción del chat para una estructura construida con {@code values}, o nula si el chat no la
   * puede construir. El texto nombra la estructura, dice «desde cero» —para que una sesión vigente
   * no la tome como un refinamiento— y lista los valores en orden.
   */
  private static Chat chat(String type, String subtype, List<Integer> values, int buckets) {
    String list = list(values);
    return switch (type + "/" + subtype) {
      case "tree/avl" ->
          new Chat(
              "Construye desde cero un árbol AVL insertando, en este orden, los valores "
                  + list
                  + ".",
              "tree",
              "avl",
              tree("avl", values));
      case "tree/bst" ->
          new Chat(
              "Construye desde cero un árbol BST insertando, en este orden, los valores "
                  + list
                  + ".",
              "tree",
              "bst",
              tree("bst", values));
      case "tree/heap" ->
          new Chat(
              "Construye desde cero un heap máximo insertando, en este orden, los valores "
                  + list
                  + ".",
              "tree",
              "heap",
              tree("heap", values));
      case "stack/simple" ->
          new Chat(
              "Construye desde cero una pila apilando, en este orden, los valores "
                  + list
                  + "; el último queda en el tope.",
              "stack",
              null,
              new StackContract("stack", null, values));
      case "queue/simple" ->
          new Chat(
              "Construye desde cero una cola encolando, en este orden, los valores "
                  + list
                  + "; el primero queda al frente.",
              "queue",
              null,
              new QueueContract("queue", null, values));
      case "linked-list/simple" ->
          new Chat(
              "Construye desde cero una lista enlazada simple con los valores, en este orden, "
                  + list
                  + ".",
              "linked-list",
              null,
              new LinkedListContract("linked-list", null, "singly", values));
      case "hash-table/chaining" ->
          buckets < 1
              ? null
              : new Chat(
                  "Construye desde cero una tabla hash de "
                      + buckets
                      + " cubetas con encadenamiento y función hash k mod "
                      + buckets
                      + ", insertando en este orden los valores "
                      + list
                      + ".",
                  "hash-table",
                  null,
                  new HashTableContract("hash-table", null, buckets, values, "modular"));
      default -> null;
    };
  }

  private static TreeContract tree(String subtype, List<Integer> values) {
    return new TreeContract(
        "tree", null, subtype, List.of(new TreeContract.Operation("insert", values)), null);
  }

  /**
   * Firma de una estructura que no depende de ids ni de posiciones: etiquetas y aristas dirigidas
   * entre etiquetas. Dos estructuras con la misma firma se ven iguales en el lienzo.
   */
  static String shape(List<Node3D> nodes, List<Edge3D> edges) {
    if (nodes == null) return "";
    Map<String, String> label = new HashMap<>();
    nodes.forEach(n -> label.put(n.id(), n.label()));
    List<String> labels = nodes.stream().map(Node3D::label).sorted().toList();
    List<String> links =
        edges == null
            ? List.of()
            : edges.stream()
                .map(e -> label.get(e.from()) + ">" + label.get(e.to()))
                .sorted()
                .toList();
    return labels + "|" + links;
  }

  private static AlgorithmRequest request(
      AlgorithmDescriptor topic,
      List<Integer> values,
      List<Node3D> nodes,
      List<Edge3D> edges,
      String start,
      Integer argument) {
    return new AlgorithmRequest(
        topic.type(),
        topic.subtype(),
        topic.operation(),
        values,
        nodes,
        edges,
        start,
        argument == null ? null : argument.doubleValue());
  }

  private StepsResponse run(AlgorithmRequest request) {
    try {
      StepsResponse response = dispatcher.run(request);
      return response == null || response.error() ? null : response;
    } catch (RuntimeException e) {
      // Entrada que el algoritmo rechaza: el sorteo se descarta.
      return null;
    }
  }

  private static List<String> facts(StepsResponse response) {
    List<String> facts = new ArrayList<>();
    for (AlgorithmStep step : response.steps()) {
      String fact = fact(step);
      if (!fact.isBlank() && (facts.isEmpty() || !facts.getLast().equals(fact))) facts.add(fact);
    }
    if (facts.size() <= MAX_FACTS) return facts;
    // Un rastro largo se recorta conservando el inicio y, sobre todo, el desenlace.
    List<String> trimmed = new ArrayList<>(facts.subList(0, MAX_FACTS / 2));
    trimmed.addAll(facts.subList(facts.size() - MAX_FACTS / 2, facts.size()));
    return trimmed;
  }

  private static String fact(AlgorithmStep step) {
    String title = step.title() == null ? "" : step.title().strip();
    String description = step.description() == null ? "" : step.description().strip();
    String fact = description.isEmpty() ? title : title + " — " + description;
    return fact.length() <= MAX_FACT_LENGTH ? fact : fact.substring(0, MAX_FACT_LENGTH) + "…";
  }

  private static String list(List<Integer> values) {
    return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  private static List<Integer> distinctValues(Random random, int size) {
    Set<Integer> values = new LinkedHashSet<>();
    while (values.size() < size) values.add(1 + random.nextInt(99));
    return List.copyOf(values);
  }

  private static int freshValue(Random random, List<Integer> values) {
    int candidate = 1 + random.nextInt(99);
    while (values.contains(candidate)) candidate = 1 + random.nextInt(99);
    return candidate;
  }
}
