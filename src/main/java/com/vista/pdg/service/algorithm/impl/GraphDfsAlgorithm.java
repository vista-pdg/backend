package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphDfsAlgorithm implements AlgorithmStrategy {
  public static final List<String> CODE =
      List.of(
          "dfs(inicio):",
          "  pila ← [inicio]; visitados ← {}; orden ← []",
          "  mientras pila no esté vacía:",
          "    u ← desapilar(pila); si u ∈ visitados: continuar",
          "    visitar u; añadir u a visitados y orden",
          "    apilar vecinos no visitados de u en orden inverso",
          "  retornar orden");

  public AlgorithmDescriptor descriptor() {
    return new AlgorithmDescriptor(
        "graph",
        "simple",
        "dfs",
        "graph",
        "DFS · Recorrido en profundidad",
        "Explora cada rama antes de retroceder",
        "structure");
  }

  public StepsResponse generate(AlgorithmRequest request) {
    try {
      GraphTrace g = new GraphTrace(request);
      Deque<String> stack = new ArrayDeque<>();
      Set<String> visited = new LinkedHashSet<>();
      stack.push(g.start);
      g.states.put(g.start, "frontier");
      g.add(
          "Inicio en " + g.label(g.start),
          "La cima de la pila se muestra primero.",
          "initial",
          List.of(g.start),
          2,
          GraphTrace.vars("pila", g.labels(stack), "orden", "[]"));
      while (!stack.isEmpty()) {
        String u = stack.pop();
        if (!visited.add(u)) continue;
        g.states.put(u, "current");
        g.add(
            "Visitar " + g.label(u),
            "Se profundiza en esta rama.",
            "visit",
            List.of(u),
            5,
            GraphTrace.vars("u", g.label(u), "pila", g.labels(stack), "orden", g.labels(visited)));
        var neighbors = g.adjacency.get(u);
        for (int i = neighbors.size() - 1; i >= 0; i--) {
          String v = neighbors.get(i).to();
          if (!visited.contains(v)) {
            stack.push(v);
            g.states.put(v, "frontier");
          }
        }
        g.states.put(u, "visited");
        g.add(
            "Pila tras " + g.label(u),
            "Se apilan los vecinos en orden inverso para explorar primero la primera arista.",
            "frontier",
            stack,
            6,
            GraphTrace.vars("u", g.label(u), "pila", g.labels(stack), "orden", g.labels(visited)));
      }
      g.add(
          "Recorrido completo",
          "Orden: " + g.labels(visited) + "." + g.unreachable(visited),
          "done",
          visited,
          7,
          GraphTrace.vars("pila", "[]", "orden", g.labels(visited)));
      return g.finish(CODE);
    } catch (IllegalArgumentException e) {
      return StepsResponse.error(e.getMessage());
    }
  }
}
