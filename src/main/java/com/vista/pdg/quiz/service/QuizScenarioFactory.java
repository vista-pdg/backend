package com.vista.pdg.quiz.service;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.AlgorithmDispatcher;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sortea la instancia concreta de una pregunta y la ejecuta con el algoritmo real.
 *
 * <p>El modelo de lenguaje se equivoca al simular algoritmos; VISTA no. Por eso el escenario lo
 * decide el servidor y los «hechos» que acompañan al prompt salen del rastro verificado del {@link
 * AlgorithmDispatcher}. Si el algoritmo no se puede ejecutar sobre la instancia sorteada, el
 * escenario viaja sin hechos y la pregunta será conceptual aplicada.
 */
@Component
@RequiredArgsConstructor
public class QuizScenarioFactory {
  static final int MAX_FACTS = 30;
  static final int MAX_FACT_LENGTH = 200;

  /**
   * Operaciones «values» que construyen la estructura sobre la que corre una operación de lienzo.
   */
  private static final List<String> BUILDERS = List.of("insert", "heapify", "push", "enqueue");

  private final AlgorithmDispatcher dispatcher;

  public record Scenario(int id, String description, List<String> facts) {}

  public Scenario build(AlgorithmDescriptor topic, Random random, int id) {
    if ("graph".equals(topic.family())) return graph(topic, random, id);
    int size = "sorting".equals(topic.family()) ? 6 : 7 + random.nextInt(2);
    List<Integer> values = distinctValues(random, size);
    if ("values".equals(topic.input())) {
      String description = "Valores de entrada, en este orden: " + values + ".";
      return new Scenario(id, description, facts(request(topic, values, null, null, null)));
    }
    return onCanvas(topic, random, id, values);
  }

  private Scenario graph(AlgorithmDescriptor topic, Random random, int id) {
    List<String> labels = List.of("A", "B", "C", "D", "E", "F");
    List<Node3D> nodes =
        labels.stream().map(l -> new Node3D(l, l, 0, 0, 0, 0, null, Map.of())).toList();
    Set<String> used = new LinkedHashSet<>();
    List<Edge3D> edges = new ArrayList<>();
    // Árbol generador aleatorio: garantiza un grafo conexo antes de añadir aristas extra.
    List<String> order = new ArrayList<>(labels);
    Collections.shuffle(order, random);
    for (int i = 1; i < order.size(); i++) {
      addEdge(edges, used, order.get(random.nextInt(i)), order.get(i), random);
    }
    for (int guard = 0; edges.size() < 9 && guard < 60; guard++) {
      String a = labels.get(random.nextInt(labels.size()));
      String b = labels.get(random.nextInt(labels.size()));
      if (!a.equals(b)) addEdge(edges, used, a, b, random);
    }
    String listed =
        String.join(
            ", ",
            edges.stream().map(e -> e.from() + "–" + e.to() + " (" + e.weight() + ")").toList());
    String description =
        "Grafo no dirigido y ponderado con vértices A, B, C, D, E y F. Aristas (peso): "
            + listed
            + ". Nodo inicial: A.";
    return new Scenario(id, description, facts(request(topic, null, nodes, edges, null)));
  }

  private static void addEdge(
      List<Edge3D> edges, Set<String> used, String a, String b, Random random) {
    String key = a.compareTo(b) < 0 ? a + b : b + a;
    if (used.add(key)) {
      edges.add(new Edge3D("e" + edges.size(), a, b, 1 + random.nextInt(9), false));
    }
  }

  /** Operación sobre la estructura del lienzo: se construye primero con su operación «values». */
  private Scenario onCanvas(
      AlgorithmDescriptor topic, Random random, int id, List<Integer> values) {
    Integer argument = null;
    String operand = "";
    if (topic.parameter() != null) {
      boolean fresh = "value".equals(topic.parameter()) || random.nextInt(4) == 0;
      argument = fresh ? freshValue(random, values) : values.get(random.nextInt(values.size()));
      operand = " Valor de la operación: " + argument + ".";
    }
    String description =
        QuizTopics.structureName(topic)
            + " construido con los valores, en este orden: "
            + values
            + "."
            + operand;
    List<String> facts = List.of();
    StepsResponse built = build(topic, values);
    if (built != null && built.steps() != null && !built.steps().isEmpty()) {
      AlgorithmStep last = built.steps().getLast();
      facts = facts(request(topic, values, last.nodes(), last.edges(), argument));
    }
    if (facts.isEmpty()) facts = facts(request(topic, values, null, null, argument));
    return new Scenario(id, description, facts);
  }

  private StepsResponse build(AlgorithmDescriptor topic, List<Integer> values) {
    return dispatcher.catalog().stream()
        .filter(
            d ->
                d.type().equals(topic.type())
                    && d.subtype().equals(topic.subtype())
                    && "values".equals(d.input())
                    && BUILDERS.contains(d.operation()))
        .findFirst()
        .map(d -> run(request(d, values, null, null, null)))
        .orElse(null);
  }

  private static AlgorithmRequest request(
      AlgorithmDescriptor topic,
      List<Integer> values,
      List<Node3D> nodes,
      List<Edge3D> edges,
      Integer argument) {
    return new AlgorithmRequest(
        topic.type(),
        topic.subtype(),
        topic.operation(),
        values,
        nodes,
        edges,
        nodes == null || nodes.isEmpty() ? null : nodes.getFirst().id(),
        argument == null ? null : argument.doubleValue());
  }

  private StepsResponse run(AlgorithmRequest request) {
    try {
      StepsResponse response = dispatcher.run(request);
      return response == null || response.error() ? null : response;
    } catch (RuntimeException e) {
      // Entrada que el algoritmo rechaza: el escenario sigue siendo válido, solo queda sin hechos.
      return null;
    }
  }

  private List<String> facts(AlgorithmRequest request) {
    StepsResponse response = run(request);
    if (response == null || response.steps() == null) return List.of();
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
