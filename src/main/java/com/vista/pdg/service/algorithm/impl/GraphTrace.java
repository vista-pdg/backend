package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import java.util.*;

/** Per-request graph and immutable snapshots shared by the graph strategies. */
final class GraphTrace {
  static final long INF = Long.MAX_VALUE / 4;
  final List<Node3D> nodes;
  final List<Edge3D> edges;
  final Map<String, Node3D> byId = new LinkedHashMap<>();
  final Map<String, List<Arc>> adjacency = new LinkedHashMap<>();
  final Map<String, String> states = new LinkedHashMap<>();
  final List<AlgorithmStep> steps = new ArrayList<>();
  final String start;

  record Arc(String to, Edge3D edge) {}

  GraphTrace(AlgorithmRequest request) {
    if (request.nodes() == null || request.nodes().isEmpty())
      throw new IllegalArgumentException("Genera primero un grafo en el lienzo.");
    nodes = List.copyOf(request.nodes());
    edges = request.edges() == null ? List.of() : List.copyOf(request.edges());
    for (Node3D n : nodes) {
      if (n.id() == null || n.id().isBlank() || byId.put(n.id(), n) != null)
        throw new IllegalArgumentException(
            "Los nodos deben tener identificadores únicos y no vacíos.");
      adjacency.put(n.id(), new ArrayList<>());
      states.put(n.id(), "unvisited");
    }
    for (Edge3D e : edges) {
      if (!byId.containsKey(e.from()) || !byId.containsKey(e.to()))
        throw new IllegalArgumentException("Cada arista debe conectar nodos del lienzo.");
      adjacency.get(e.from()).add(new Arc(e.to(), e));
      if (!e.directed()) adjacency.get(e.to()).add(new Arc(e.from(), e));
    }
    if (request.start() != null && !byId.containsKey(request.start()))
      throw new IllegalArgumentException(
          "El nodo inicial ya no está en el lienzo; selecciona otro.");
    start = request.start() == null ? nodes.getFirst().id() : request.start();
  }

  void requireUndirected() {
    if (edges.stream().anyMatch(Edge3D::directed))
      throw new IllegalArgumentException(
          "Este algoritmo requiere un grafo no dirigido. Genera uno sin flechas.");
  }

  static long weight(Edge3D edge) {
    return edge.weight() == null ? 1 : edge.weight();
  }

  static String number(long value) {
    return value == INF ? "∞" : Long.toString(value);
  }

  String label(String id) {
    return byId.get(id).label();
  }

  String labels(Collection<String> ids) {
    return "[" + String.join(", ", ids.stream().map(this::label).toList()) + "]";
  }

  String distances(Map<String, Long> distances) {
    return String.join(
        ", ",
        distances.entrySet().stream()
            .map(e -> label(e.getKey()) + ": " + number(e.getValue()))
            .toList());
  }

  String edgeLabels(Collection<Edge3D> selected) {
    return "["
        + String.join(
            ", ",
            selected.stream()
                .map(e -> label(e.from()) + "–" + label(e.to()) + " (" + weight(e) + ")")
                .toList())
        + "]";
  }

  String unreachable(Collection<String> reached) {
    var missing = byId.keySet().stream().filter(id -> !reached.contains(id)).toList();
    return missing.isEmpty() ? "" : " No alcanzables desde el inicio: " + labels(missing) + ".";
  }

  void add(
      String title,
      String description,
      String type,
      Collection<String> highlighted,
      int line,
      Map<String, String> variables) {
    var snapshot =
        nodes.stream()
            .map(
                n -> {
                  Map<String, Object> props =
                      new LinkedHashMap<>(n.properties() == null ? Map.of() : n.properties());
                  props.put("state", states.get(n.id()));
                  return new Node3D(
                      n.id(), n.label(), n.x(), n.y(), n.z(), n.depth(), n.parent(), props);
                })
            .toList();
    steps.add(
        new AlgorithmStep(
            steps.size(),
            title,
            description,
            type,
            List.copyOf(new LinkedHashSet<>(highlighted)),
            null,
            snapshot,
            edges,
            line,
            Collections.unmodifiableMap(new LinkedHashMap<>(variables)),
            null));
  }

  StepsResponse finish(List<String> code) {
    return StepsResponse.ok(List.copyOf(steps), code, "pseudocode");
  }

  static Map<String, String> vars(String... entries) {
    Map<String, String> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) result.put(entries[i], entries[i + 1]);
    return result;
  }
}
