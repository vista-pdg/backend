package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.Node3D;
import java.util.*;

/**
 * Validates topology before recursion/search, including disconnected cycles and parent conflicts.
 */
final class TreeIndex {
  final CanvasTrace trace;
  final Map<String, List<String>> children = new LinkedHashMap<>();
  final Map<String, String> parent = new HashMap<>();
  final String root;

  TreeIndex(CanvasTrace t) {
    trace = t;
    t.nodes.forEach(n -> children.put(n.id(), new ArrayList<>()));
    for (Node3D n : t.nodes) if (n.parent() != null) attach(n.parent(), n.id());
    t.edges.forEach(
        e -> {
          if (!e.directed())
            throw new InvalidAlgorithmInputException(
                "El árbol requiere aristas dirigidas de padre a hijo.");
          attach(e.from(), e.to());
        });
    var roots = t.nodes.stream().map(Node3D::id).filter(id -> !parent.containsKey(id)).toList();
    if (roots.size() != 1)
      throw new InvalidAlgorithmInputException("Se requiere un árbol conectado con una sola raíz.");
    root = roots.getFirst();
    Set<String> visited = new HashSet<>();
    walk(root, visited);
    if (visited.size() != t.nodes.size())
      throw new InvalidAlgorithmInputException("El árbol tiene nodos desconectados o ciclos.");
    children
        .values()
        .forEach(kids -> kids.sort(Comparator.comparingDouble(id -> t.byId.get(id).x())));
  }

  private void attach(String from, String to) {
    if (!children.containsKey(from) || !children.containsKey(to) || from.equals(to))
      throw new InvalidAlgorithmInputException("Padre inválido en el árbol.");
    String old = parent.putIfAbsent(to, from);
    if (old != null && !old.equals(from))
      throw new InvalidAlgorithmInputException("Un nodo no puede tener dos padres.");
    if (!children.get(from).contains(to)) children.get(from).add(to);
  }

  private void walk(String id, Set<String> seen) {
    if (!seen.add(id)) throw new InvalidAlgorithmInputException("El árbol contiene un ciclo.");
    for (String k : children.get(id)) walk(k, seen);
  }

  void requireBst() {
    checkBst(root, Long.MIN_VALUE, Long.MAX_VALUE);
  }

  private void checkBst(String id, long lo, long hi) {
    int v = CanvasTrace.value(trace.byId.get(id));
    if (v <= lo || v >= hi || children.get(id).size() > 2)
      throw new InvalidAlgorithmInputException(
          "El lienzo no cumple la propiedad de un BST sin duplicados.");
    boolean left = false, right = false;
    for (String k : children.get(id)) {
      int kv = CanvasTrace.value(trace.byId.get(k));
      if (kv < v) {
        if (left) throw new InvalidAlgorithmInputException("El BST tiene dos hijos izquierdos.");
        left = true;
        checkBst(k, lo, v);
      } else {
        if (right) throw new InvalidAlgorithmInputException("El BST tiene dos hijos derechos.");
        right = true;
        checkBst(k, v, hi);
      }
    }
    children.get(id).sort(Comparator.comparingInt(k -> CanvasTrace.value(trace.byId.get(k))));
  }
}
