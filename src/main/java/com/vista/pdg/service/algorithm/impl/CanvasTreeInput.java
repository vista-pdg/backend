package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import java.util.*;

/** Validates the original tree and restores its IDs after value-based tree snapshots. */
final class CanvasTreeInput {
  final CanvasTrace trace;
  final TreeIndex tree;

  CanvasTreeInput(AlgorithmRequest r, boolean avl) {
    trace = new CanvasTrace(r);
    if (trace.nodes.stream()
        .anyMatch(
            n ->
                n.properties() != null
                    && (n.properties().containsKey("role")
                        || Boolean.TRUE.equals(n.properties().get("bucket")))))
      throw new InvalidAlgorithmInputException("Se requiere un árbol de búsqueda.");
    tree = new TreeIndex(trace);
    tree.requireBst();
    if (avl) height(tree.root);
    if (trace.nodes.size() >= 64
        && trace.nodes.stream().noneMatch(n -> CanvasTrace.value(n) == CanvasTrace.argument(r)))
      throw new InvalidAlgorithmInputException("El árbol admite hasta 64 nodos.");
  }

  private int height(String id) {
    if (id == null) return 0;
    int v = CanvasTrace.value(trace.byId.get(id));
    int left = 0, right = 0;
    for (String child : tree.children.get(id)) {
      int h = height(child);
      if (CanvasTrace.value(trace.byId.get(child)) < v) left = h;
      else right = h;
    }
    if (Math.abs(left - right) > 1)
      throw new InvalidAlgorithmInputException("El árbol del lienzo no está balanceado como AVL.");
    return 1 + Math.max(left, right);
  }

  List<Node3D> nodesWithParents() {
    return trace.nodes.stream()
        .map(
            n ->
                new Node3D(
                    n.id(),
                    n.label(),
                    n.x(),
                    n.y(),
                    n.z(),
                    n.depth(),
                    tree.parent.get(n.id()),
                    n.properties()))
        .toList();
  }

  List<Integer> preorder() {
    List<Integer> values = new ArrayList<>();
    preorder(tree.root, values);
    return values;
  }

  private void preorder(String id, List<Integer> values) {
    values.add(CanvasTrace.value(trace.byId.get(id)));
    for (String child : tree.children.get(id)) preorder(child, values);
  }

  StepsResponse restore(StepsResponse response) {
    Map<String, Node3D> originals = new HashMap<>();
    trace.nodes.forEach(n -> originals.put("" + CanvasTrace.value(n), n));
    Map<String, String> ids = new HashMap<>();
    Set<String> used = new HashSet<>(trace.byId.keySet());
    for (var step : response.steps())
      for (var n : step.index() == 0 ? List.<Node3D>of() : step.nodes()) {
        if (ids.containsKey(n.id())) continue;
        Node3D original = originals.get(n.label());
        String id = original == null ? n.id() : original.id();
        if (original == null) {
          String base = id;
          for (int i = 1; used.contains(id); i++) id = base + ":" + i;
        }
        used.add(id);
        ids.put(n.id(), id);
      }
    List<AlgorithmStep> steps = new ArrayList<>();
    for (var step : response.steps()) {
      List<Node3D> nodes =
          step.index() == 0
              ? trace.nodes
              : step.nodes().stream()
                  .map(
                      n -> {
                        Node3D original = originals.get(n.label());
                        Map<String, Object> props = new LinkedHashMap<>();
                        if (original != null && original.properties() != null)
                          props.putAll(original.properties());
                        if (n.properties() != null) props.putAll(n.properties());
                        return new Node3D(
                            ids.get(n.id()),
                            n.label(),
                            n.x(),
                            n.y(),
                            n.z(),
                            n.depth(),
                            n.parent() == null ? null : ids.get(n.parent()),
                            Map.copyOf(props));
                      })
                  .toList();
      List<Edge3D> edges =
          step.index() == 0
              ? trace.edges
              : step.edges().stream()
                  .map(
                      e -> {
                        String from = ids.get(e.from()), to = ids.get(e.to());
                        return trace.edges.stream()
                            .filter(old -> old.from().equals(from) && old.to().equals(to))
                            .findFirst()
                            .orElseGet(
                                () ->
                                    new Edge3D(
                                        "tree:" + from + ":" + to,
                                        from,
                                        to,
                                        e.weight(),
                                        e.directed()));
                      })
                  .toList();
      steps.add(
          new AlgorithmStep(
              step.index(),
              step.title(),
              step.description(),
              step.highlightType(),
              step.highlightedNodeIds().stream().map(id -> ids.getOrDefault(id, id)).toList(),
              step.rotationType(),
              nodes,
              edges,
              step.line(),
              step.variables(),
              step.callStack()));
    }
    return new StepsResponse(
        response.error(),
        response.message(),
        List.copyOf(steps),
        response.code(),
        response.language(),
        response.representations());
  }
}
