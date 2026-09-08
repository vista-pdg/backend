package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Recorrido en anchura sobre la estructura del lienzo (HU-19).
 *
 * <p>El rastro es determinista: los vecinos se descubren en el orden de las aristas de la petición
 * y la cola es FIFO. Cada paso lleva la instantánea completa con {@code properties.state} por nodo
 * ({@code unvisited} / {@code frontier} / {@code current} / {@code visited}) además del resaltado,
 * para que ambos renderizadores pinten el mismo cuadro. Las posiciones de los nodos son las que
 * llegaron: el algoritmo no mueve nada.
 */
@Service
public class GraphBfsAlgorithm implements AlgorithmStrategy {

  private static final AlgorithmDescriptor DESCRIPTOR =
      new AlgorithmDescriptor(
          "graph",
          "simple",
          "bfs",
          "graph",
          "BFS · Recorrido en anchura",
          "Sobre el grafo del lienzo, desde un nodo inicial",
          "structure");

  @Override
  public AlgorithmDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public StepsResponse generate(AlgorithmRequest req) {
    if (req.nodes() == null || req.nodes().isEmpty()) {
      return StepsResponse.error("BFS necesita un grafo en el lienzo: genera uno primero");
    }
    List<Node3D> nodes = req.nodes();
    List<Edge3D> edges = req.edges() == null ? List.of() : req.edges();
    Map<String, Node3D> byId = new LinkedHashMap<>();
    for (Node3D n : nodes) byId.put(n.id(), n);

    String start =
        req.start() != null && byId.containsKey(req.start()) ? req.start() : nodes.getFirst().id();
    Map<String, List<String>> adjacency = adjacency(nodes, edges);

    Map<String, String> state = new HashMap<>();
    for (Node3D n : nodes) state.put(n.id(), "unvisited");

    List<AlgorithmStep> steps = new ArrayList<>();
    List<String> order = new ArrayList<>();
    Deque<String> queue = new ArrayDeque<>();
    Set<String> discovered = new LinkedHashSet<>();

    queue.add(start);
    discovered.add(start);
    state.put(start, "frontier");
    steps.add(
        step(
            steps.size(),
            "Inicio en " + label(byId, start),
            "Se encola el nodo inicial "
                + label(byId, start)
                + ". Cola: ["
                + label(byId, start)
                + "]",
            "frontier",
            List.of(start),
            nodes,
            edges,
            state));

    while (!queue.isEmpty()) {
      String current = queue.poll();
      state.put(current, "current");
      order.add(current);
      List<String> newly = new ArrayList<>();
      for (String nb : adjacency.getOrDefault(current, List.of())) {
        if (discovered.add(nb)) {
          newly.add(nb);
          queue.add(nb);
        }
      }
      steps.add(
          step(
              steps.size(),
              "Visitar " + label(byId, current),
              "Se desencola "
                  + label(byId, current)
                  + " y se visita. Orden hasta ahora: "
                  + labels(byId, order),
              "visit",
              List.of(current),
              nodes,
              edges,
              state));
      if (!newly.isEmpty()) {
        for (String nb : newly) state.put(nb, "frontier");
        steps.add(
            step(
                steps.size(),
                "Descubrir vecinos de " + label(byId, current),
                "Se encolan "
                    + labels(byId, newly)
                    + ". Cola: "
                    + labels(byId, new ArrayList<>(queue)),
                "frontier",
                newly,
                nodes,
                edges,
                state));
      }
      state.put(current, "visited");
    }

    List<String> unreached =
        nodes.stream().map(Node3D::id).filter(id -> !discovered.contains(id)).toList();
    String tail =
        unreached.isEmpty()
            ? ""
            : " No alcanzables desde el inicio: " + labels(byId, unreached) + ".";
    steps.add(
        step(
            steps.size(),
            "Recorrido completo",
            "Orden de visita: " + labels(byId, order) + "." + tail,
            "done",
            order,
            nodes,
            edges,
            state));
    return StepsResponse.ok(steps);
  }

  private static Map<String, List<String>> adjacency(List<Node3D> nodes, List<Edge3D> edges) {
    Map<String, List<String>> adj = new LinkedHashMap<>();
    for (Node3D n : nodes) adj.put(n.id(), new ArrayList<>());
    for (Edge3D e : edges) {
      if (!adj.containsKey(e.from()) || !adj.containsKey(e.to())) continue;
      adj.get(e.from()).add(e.to());
      if (!e.directed()) adj.get(e.to()).add(e.from());
    }
    return adj;
  }

  private static AlgorithmStep step(
      int index,
      String title,
      String description,
      String highlightType,
      List<String> highlighted,
      List<Node3D> nodes,
      List<Edge3D> edges,
      Map<String, String> state) {
    List<Node3D> snapshot =
        nodes.stream()
            .map(
                n -> {
                  Map<String, Object> props =
                      new LinkedHashMap<>(n.properties() == null ? Map.of() : n.properties());
                  props.put("state", state.get(n.id()));
                  return new Node3D(
                      n.id(), n.label(), n.x(), n.y(), n.z(), n.depth(), n.parent(), props);
                })
            .toList();
    return new AlgorithmStep(
        index, title, description, highlightType, List.copyOf(highlighted), null, snapshot, edges);
  }

  private static String label(Map<String, Node3D> byId, String id) {
    Node3D n = byId.get(id);
    return n == null ? id : n.label();
  }

  private static String labels(Map<String, Node3D> byId, List<String> ids) {
    return "[" + String.join(", ", ids.stream().map(id -> label(byId, id)).toList()) + "]";
  }
}
