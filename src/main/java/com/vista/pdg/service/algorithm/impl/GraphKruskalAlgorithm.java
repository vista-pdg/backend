package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphKruskalAlgorithm implements AlgorithmStrategy {
  public static final List<String> CODE =
      List.of(
          "kruskal(grafo):",
          "  crear un conjunto por nodo; bosque ← []; costo ← 0",
          "  ordenar aristas por peso ascendente",
          "  para cada arista e = (u, v):",
          "    si encontrar(u) = encontrar(v): descartar e (ciclo)",
          "    si no: unir conjuntos; añadir e al bosque; sumar peso",
          "  retornar bosque y costo");

  public AlgorithmDescriptor descriptor() {
    return new AlgorithmDescriptor(
        "graph",
        "simple",
        "kruskal",
        "graph",
        "Kruskal · Bosque de expansión mínima",
        "Grafo no dirigido; ordena aristas por peso",
        "structure");
  }

  public StepsResponse generate(AlgorithmRequest request) {
    try {
      GraphTrace g = new GraphTrace(request);
      g.requireUndirected();
      Map<String, String> parent = new LinkedHashMap<>();
      g.byId.keySet().forEach(id -> parent.put(id, id));
      List<Edge3D> sorted = new ArrayList<>(g.edges);
      sorted.sort(Comparator.comparingLong(GraphTrace::weight));
      List<Edge3D> chosen = new ArrayList<>();
      long cost = 0;
      int components = g.nodes.size();
      g.add(
          "Aristas ordenadas",
          "Se consideran por peso ascendente. Sin peso = 1; los empates conservan el orden del lienzo.",
          "initial",
          List.of(),
          3,
          GraphTrace.vars(
              "pendientes",
              g.edgeLabels(sorted),
              "aristas",
              "[]",
              "costo",
              "0",
              "componentes",
              Integer.toString(components)));
      for (Edge3D e : sorted) {
        String a = find(parent, e.from()), b = find(parent, e.to());
        boolean cycle = a.equals(b);
        if (!cycle) {
          parent.put(a, b);
          chosen.add(e);
          cost += GraphTrace.weight(e);
          components--;
          g.states.put(e.from(), "visited");
          g.states.put(e.to(), "visited");
        }
        g.add(
            (cycle ? "Descartar " : "Añadir ") + g.label(e.from()) + "–" + g.label(e.to()),
            cycle
                ? "Sus extremos ya están conectados; añadirla formaría un ciclo."
                : "Une dos componentes diferentes. Costo acumulado: " + cost + ".",
            cycle ? "frontier" : "visit",
            List.of(e.from(), e.to()),
            cycle ? 5 : 6,
            GraphTrace.vars(
                "arista",
                g.edgeLabels(List.of(e)),
                "aristas",
                g.edgeLabels(chosen),
                "costo",
                Long.toString(cost),
                "componentes",
                Integer.toString(components)));
      }
      g.byId.keySet().forEach(id -> g.states.put(id, "visited"));
      g.add(
          components == 1 ? "Árbol de expansión mínima" : "Bosque de expansión mínima",
          "Componentes: "
              + components
              + ". Costo total: "
              + cost
              + ". Aristas: "
              + g.edgeLabels(chosen),
          "done",
          g.byId.keySet(),
          7,
          GraphTrace.vars(
              "aristas",
              g.edgeLabels(chosen),
              "costo",
              Long.toString(cost),
              "componentes",
              Integer.toString(components)));
      return g.finish(CODE);
    } catch (IllegalArgumentException e) {
      return StepsResponse.error(e.getMessage());
    }
  }

  private static String find(Map<String, String> parent, String id) {
    while (!parent.get(id).equals(id)) {
      parent.put(id, parent.get(parent.get(id)));
      id = parent.get(id);
    }
    return id;
  }
}
