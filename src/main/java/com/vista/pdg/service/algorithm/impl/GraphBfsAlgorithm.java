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
 * Recorrido en anchura sobre la estructura del lienzo (HU-19), instrumentado (HU-22b).
 *
 * <p>El rastro es determinista: los vecinos se descubren en el orden de las aristas de la petición
 * y la cola es FIFO. Cada paso lleva la instantánea completa con {@code properties.state} por nodo
 * ({@code unvisited} / {@code frontier} / {@code current} / {@code visited}), el resaltado, la
 * línea del pseudocódigo y las variables ({@code u}, {@code cola}, {@code visitados}, {@code
 * orden}). La forma del rastro (número y orden de pasos) es la de HU-19: sólo se añadió
 * instrumentación.
 */
@Service
public class GraphBfsAlgorithm implements AlgorithmStrategy {

  public static final List<String> CODE =
      List.of(
          "bfs(inicio):",
          "  cola ← [inicio]; visitados ← {inicio}",
          "  mientras cola no esté vacía:",
          "    u ← desencolar(cola)",
          "    visitar(u)",
          "    para cada v vecino de u:",
          "      si v ∉ visitados: marcar v y encolar v",
          "  retornar orden");

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
    String[] current = {null};

    java.util.function.Function<Void, Map<String, String>> vars =
        v -> {
          Map<String, String> m = new LinkedHashMap<>();
          m.put("u", current[0] == null ? "—" : label(byId, current[0]));
          m.put("cola", labels(byId, new ArrayList<>(queue)));
          m.put(
              "visitados",
              "{"
                  + String.join(", ", discovered.stream().map(id -> label(byId, id)).toList())
                  + "}");
          m.put("orden", labels(byId, order));
          return m;
        };

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
            state,
            2,
            vars.apply(null)));

    while (!queue.isEmpty()) {
      String cur = queue.poll();
      current[0] = cur;
      state.put(cur, "current");
      order.add(cur);
      List<String> newly = new ArrayList<>();
      for (String nb : adjacency.getOrDefault(cur, List.of())) {
        if (discovered.add(nb)) {
          newly.add(nb);
          queue.add(nb);
        }
      }
      // Instantánea de variables ANTES de encolar los vecinos, que es lo que la línea 4-5 ve.
      Map<String, String> atVisit = new LinkedHashMap<>();
      atVisit.put("u", label(byId, cur));
      List<String> queueBefore = new ArrayList<>(queue);
      queueBefore.removeAll(newly);
      atVisit.put("cola", labels(byId, queueBefore));
      Set<String> discBefore = new LinkedHashSet<>(discovered);
      discBefore.removeAll(newly);
      atVisit.put(
          "visitados",
          "{" + String.join(", ", discBefore.stream().map(id -> label(byId, id)).toList()) + "}");
      atVisit.put("orden", labels(byId, order));
      steps.add(
          step(
              steps.size(),
              "Visitar " + label(byId, cur),
              "Se desencola "
                  + label(byId, cur)
                  + " y se visita. Orden hasta ahora: "
                  + labels(byId, order),
              "visit",
              List.of(cur),
              nodes,
              edges,
              state,
              5,
              atVisit));
      if (!newly.isEmpty()) {
        for (String nb : newly) state.put(nb, "frontier");
        steps.add(
            step(
                steps.size(),
                "Descubrir vecinos de " + label(byId, cur),
                "Se encolan "
                    + labels(byId, newly)
                    + ". Cola: "
                    + labels(byId, new ArrayList<>(queue)),
                "frontier",
                newly,
                nodes,
                edges,
                state,
                7,
                vars.apply(null)));
      }
      state.put(cur, "visited");
    }

    current[0] = null;
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
            state,
            8,
            vars.apply(null)));
    return StepsResponse.ok(steps, CODE, "pseudocode");
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
      Map<String, String> state,
      int line,
      Map<String, String> variables) {
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
        index,
        title,
        description,
        highlightType,
        List.copyOf(highlighted),
        null,
        snapshot,
        edges,
        line,
        variables,
        null);
  }

  private static String label(Map<String, Node3D> byId, String id) {
    Node3D n = byId.get(id);
    return n == null ? id : n.label();
  }

  private static String labels(Map<String, Node3D> byId, List<String> ids) {
    return "[" + String.join(", ", ids.stream().map(id -> label(byId, id)).toList()) + "]";
  }
}
