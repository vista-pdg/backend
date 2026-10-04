package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphPrimAlgorithm implements AlgorithmStrategy {
  public static final List<String> CODE =
      List.of(
          "prim(grafo, inicio):", "  visitados ← {}; bosque ← []; costo ← 0",
          "  para cada raíz no visitada, empezando por inicio:", "    añadir raíz a visitados",
          "    mientras exista una arista que salga de visitados:",
              "      e ← arista de menor peso con un solo extremo visitado",
          "      añadir e al bosque y su otro extremo a visitados; sumar peso",
              "  retornar bosque y costo");

  public AlgorithmDescriptor descriptor() {
    return new AlgorithmDescriptor(
        "graph",
        "simple",
        "prim",
        "graph",
        "Prim · Bosque de expansión mínima",
        "Grafo no dirigido; crece desde un nodo",
        "structure");
  }

  public StepsResponse generate(AlgorithmRequest request) {
    try {
      GraphTrace g = new GraphTrace(request);
      g.requireUndirected();
      Set<String> visited = new LinkedHashSet<>();
      List<Edge3D> chosen = new ArrayList<>();
      long cost = 0;
      int components = 0;
      g.add(
          "Bosque inicial",
          "Aristas sin peso valen 1. En grafos desconectados se procesa cada componente.",
          "initial",
          List.of(),
          2,
          GraphTrace.vars("costo", "0", "aristas", "[]", "componentes", "0"));
      List<String> roots = new ArrayList<>();
      roots.add(g.start);
      g.byId.keySet().stream().filter(id -> !id.equals(g.start)).forEach(roots::add);
      for (String root : roots) {
        if (!visited.add(root)) continue;
        components++;
        g.states.put(root, "visited");
        g.add(
            "Componente desde " + g.label(root),
            "Se inicia un árbol de expansión en esta componente.",
            "visit",
            List.of(root),
            4,
            GraphTrace.vars(
                "visitados",
                g.labels(visited),
                "costo",
                Long.toString(cost),
                "aristas",
                g.edgeLabels(chosen),
                "componentes",
                Integer.toString(components)));
        while (true) {
          Edge3D best = null;
          for (Edge3D e : g.edges) {
            if (visited.contains(e.from()) == visited.contains(e.to())) continue;
            if (best == null || GraphTrace.weight(e) < GraphTrace.weight(best)) best = e;
          }
          if (best == null) break;
          String next = visited.contains(best.from()) ? best.to() : best.from();
          visited.add(next);
          chosen.add(best);
          cost += GraphTrace.weight(best);
          g.states.put(next, "visited");
          g.add(
              "Añadir " + g.label(best.from()) + "–" + g.label(best.to()),
              "Es la arista más barata que conecta un nodo nuevo. Costo acumulado: " + cost + ".",
              "visit",
              List.of(best.from(), best.to()),
              7,
              GraphTrace.vars(
                  "visitados",
                  g.labels(visited),
                  "costo",
                  Long.toString(cost),
                  "aristas",
                  g.edgeLabels(chosen),
                  "componentes",
                  Integer.toString(components)));
        }
      }
      g.add(
          components == 1 ? "Árbol de expansión mínima" : "Bosque de expansión mínima",
          "Componentes: "
              + components
              + ". Costo total: "
              + cost
              + ". Aristas: "
              + g.edgeLabels(chosen),
          "done",
          visited,
          8,
          GraphTrace.vars(
              "costo",
              Long.toString(cost),
              "aristas",
              g.edgeLabels(chosen),
              "componentes",
              Integer.toString(components)));
      return g.finish(CODE);
    } catch (IllegalArgumentException e) {
      return StepsResponse.error(e.getMessage());
    }
  }
}
