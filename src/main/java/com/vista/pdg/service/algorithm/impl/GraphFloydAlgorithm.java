package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphFloydAlgorithm implements AlgorithmStrategy {
  public static final List<String> CODE =
      List.of(
          "floydWarshall(grafo):", "  D[i,i] ← 0; D[i,j] ← menor peso directo o ∞",
          "  para cada nodo intermedio k:", "    para cada origen i y destino j:",
          "      si D[i,k] y D[k,j] son finitas:",
              "        D[i,j] ← mínimo(D[i,j], D[i,k] + D[k,j])",
          "    si algún D[i,i] < 0: error de ciclo negativo", "  retornar D");

  public AlgorithmDescriptor descriptor() {
    return new AlgorithmDescriptor(
        "graph",
        "simple",
        "floyd",
        "graph",
        "Floyd–Warshall · Todos los pares",
        "Distancias entre todos los nodos; sin origen",
        "structure");
  }

  public StepsResponse generate(AlgorithmRequest request) {
    try {
      GraphTrace g = new GraphTrace(request);
      // Each educational snapshot includes the full matrix: keep response size and O(n³) bounded.
      if (g.nodes.size() > 40)
        return StepsResponse.error(
            "Floyd–Warshall admite hasta 40 nodos en la visualización. Genera un grafo más pequeño.");
      List<String> ids = new ArrayList<>(g.byId.keySet());
      int n = ids.size();
      long[][] d = new long[n][n];
      for (int i = 0; i < n; i++) {
        Arrays.fill(d[i], GraphTrace.INF);
        d[i][i] = 0;
      }
      for (int i = 0; i < n; i++)
        for (var arc : g.adjacency.get(ids.get(i))) {
          int j = ids.indexOf(arc.to());
          d[i][j] = Math.min(d[i][j], GraphTrace.weight(arc.edge()));
        }
      g.add(
          "Matriz inicial",
          "Filas = origen; columnas = destino. ∞ indica ausencia de camino. Sin peso = 1.",
          "initial",
          List.of(),
          2,
          variables(g, ids, d, "—", "0"));
      if (negativeCycle(d))
        return StepsResponse.error(
            "El grafo contiene un ciclo negativo: no existen distancias mínimas finitas.");
      for (int k = 0; k < n; k++) {
        int updates = 0;
        for (int i = 0; i < n; i++)
          for (int j = 0; j < n; j++) {
            if (d[i][k] != GraphTrace.INF
                && d[k][j] != GraphTrace.INF
                && d[i][k] + d[k][j] < d[i][j]) {
              d[i][j] = d[i][k] + d[k][j];
              updates++;
            }
          }
        // Stop before further passes can magnify a negative cycle into integer overflow.
        if (negativeCycle(d))
          return StepsResponse.error(
              "El grafo contiene un ciclo negativo: no existen distancias mínimas finitas.");
        String id = ids.get(k);
        g.states.put(id, "current");
        g.add(
            "Intermedio " + g.label(id),
            "Se evalúan todos los pares usando "
                + g.label(id)
                + ". Distancias mejoradas: "
                + updates
                + ".",
            "visit",
            List.of(id),
            6,
            variables(g, ids, d, g.label(id), Integer.toString(updates)));
        g.states.put(id, "visited");
      }
      g.add(
          "Distancias entre todos los pares",
          "La matriz contiene las distancias mínimas. Los pares sin camino permanecen en ∞.",
          "done",
          ids,
          8,
          variables(g, ids, d, "—", "—"));
      return g.finish(CODE);
    } catch (IllegalArgumentException e) {
      return StepsResponse.error(e.getMessage());
    }
  }

  private static boolean negativeCycle(long[][] d) {
    for (int i = 0; i < d.length; i++) if (d[i][i] < 0) return true;
    return false;
  }

  private static Map<String, String> variables(
      GraphTrace g, List<String> ids, long[][] d, String k, String updates) {
    Map<String, String> vars =
        GraphTrace.vars("k", k, "mejoras", updates, "columnas", g.labels(ids));
    for (int i = 0; i < ids.size(); i++) {
      List<String> row = new ArrayList<>();
      for (long value : d[i]) row.add(GraphTrace.number(value));
      vars.put(
          "D[" + ids.get(i) + "] · " + g.label(ids.get(i)), "[" + String.join(", ", row) + "]");
    }
    return vars;
  }
}
