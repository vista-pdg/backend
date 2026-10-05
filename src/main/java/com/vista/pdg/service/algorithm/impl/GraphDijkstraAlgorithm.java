package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphDijkstraAlgorithm implements AlgorithmStrategy {
  public static final List<String> CODE =
      List.of(
          "dijkstra(inicio):", "  distancias ← ∞; distancia[inicio] ← 0; fijos ← {}",
          "  mientras exista un nodo alcanzable sin fijar:",
              "    u ← nodo no fijo con menor distancia; fijar u",
          "    para cada arista u → v de peso w:", "      si distancia[u] + w < distancia[v]:",
          "        distancia[v] ← distancia[u] + w; anterior[v] ← u",
              "  retornar distancias y anteriores");

  public AlgorithmDescriptor descriptor() {
    return new AlgorithmDescriptor(
        "graph",
        "simple",
        "dijkstra",
        "graph",
        "Dijkstra · Caminos mínimos",
        "Distancias desde un origen; pesos no negativos",
        "structure");
  }

  public StepsResponse generate(AlgorithmRequest request) {
    try {
      GraphTrace g = new GraphTrace(request);
      if (g.edges.stream().anyMatch(e -> GraphTrace.weight(e) < 0))
        return StepsResponse.error(
            "Dijkstra requiere pesos no negativos. Usa Floyd–Warshall para pesos negativos sin ciclos negativos.");
      Map<String, Long> distance = new LinkedHashMap<>();
      Map<String, String> previous = new LinkedHashMap<>();
      Set<String> fixed = new LinkedHashSet<>();
      g.byId.keySet().forEach(id -> distance.put(id, GraphTrace.INF));
      distance.put(g.start, 0L);
      g.states.put(g.start, "frontier");
      g.add(
          "Distancias iniciales",
          "El origen vale 0; ∞ significa no alcanzable. Aristas sin peso valen 1.",
          "initial",
          List.of(g.start),
          2,
          GraphTrace.vars("distancias", g.distances(distance), "anteriores", "{}"));
      while (true) {
        String u = null;
        for (String id : g.byId.keySet())
          if (!fixed.contains(id)
              && distance.get(id) < GraphTrace.INF
              && (u == null || distance.get(id) < distance.get(u))) u = id;
        if (u == null) break;
        fixed.add(u);
        g.states.put(u, "current");
        g.add(
            "Fijar " + g.label(u),
            "Su distancia mínima es " + distance.get(u) + ".",
            "visit",
            List.of(u),
            4,
            GraphTrace.vars(
                "u",
                g.label(u),
                "distancias",
                g.distances(distance),
                "anteriores",
                previous.toString()));
        for (var arc : g.adjacency.get(u)) {
          String v = arc.to();
          long candidate = distance.get(u) + GraphTrace.weight(arc.edge());
          if (!fixed.contains(v) && candidate < distance.get(v)) {
            distance.put(v, candidate);
            previous.put(v, u);
            g.states.put(v, "frontier");
            g.add(
                "Mejorar " + g.label(v),
                "Camino por " + g.label(u) + ": distancia " + candidate + ".",
                "frontier",
                List.of(u, v),
                7,
                GraphTrace.vars(
                    "u",
                    g.label(u),
                    "v",
                    g.label(v),
                    "distancias",
                    g.distances(distance),
                    "anteriores",
                    previous.toString()));
          }
        }
        g.states.put(u, "visited");
      }
      g.add(
          "Caminos mínimos calculados",
          g.distances(distance) + "." + g.unreachable(fixed),
          "done",
          fixed,
          8,
          GraphTrace.vars("distancias", g.distances(distance), "anteriores", previous.toString()));
      return g.finish(CODE);
    } catch (IllegalArgumentException e) {
      return StepsResponse.error(e.getMessage());
    }
  }
}
