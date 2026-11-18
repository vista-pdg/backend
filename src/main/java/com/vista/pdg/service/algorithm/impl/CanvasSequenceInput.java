package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.AlgorithmCode;
import java.util.*;

/** Native canvas operations keep element identity, including duplicate labels. */
final class CanvasSequenceInput {
  private CanvasSequenceInput() {}

  static boolean present(AlgorithmRequest r) {
    return r.nodes() != null && !r.nodes().isEmpty();
  }

  static List<Node3D> read(CanvasTrace t, String kind) {
    var items = SequenceScene.ordered(t);
    items.forEach(CanvasTrace::value);
    boolean stack = kind.equals("stack"), queue = kind.equals("queue");
    if (stack || queue) {
      for (int i = 0; i < items.size(); i++) {
        var n = items.get(i);
        String role =
            stack
                ? (i == items.size() - 1 ? "top" : i == 0 ? "bottom" : "middle")
                : (i == 0 ? "front" : i == items.size() - 1 ? "rear" : "middle");
        if (n.parent() != null
            || n.properties() == null
            || !role.equals(n.properties().get("role")))
          throw new InvalidAlgorithmInputException(
              "El lienzo no corresponde a una " + (stack ? "pila" : "cola") + " válida.");
      }
      if (stack && !t.edges.isEmpty())
        throw new InvalidAlgorithmInputException("La pila no debe contener enlaces.");
      if (queue) SequenceScene.validateListLinks(t, items, false, false);
    } else if (items.stream()
        .anyMatch(
            n ->
                n.parent() != null
                    || (n.properties() != null
                        && (n.properties().containsKey("role")
                            || Boolean.TRUE.equals(n.properties().get("bucket")))))) {
      throw new InvalidAlgorithmInputException("Se requiere una lista enlazada para ordenar.");
    }
    return items;
  }

  static StepsResponse remove(AlgorithmRequest r, String kind) {
    CanvasTrace t = new CanvasTrace(r);
    List<Node3D> items = read(t, kind);
    boolean stack = kind.equals("stack");
    List<String> removed = new ArrayList<>();
    t.add(
        "Estructura del lienzo",
        "Se usa la figura existente.",
        "initial",
        1,
        List.of(),
        Map.of("tamaño", "" + items.size()));
    while (!items.isEmpty()) {
      Node3D n = stack ? items.getLast() : items.getFirst();
      var before = retained(t, items, kind);
      t.add(
          before.nodes(),
          before.edges(),
          (stack ? "pop(): tope " : "dequeue(): frente ") + n.label(),
          "Se retira el elemento accesible.",
          stack ? "pop" : "dequeue",
          3,
          List.of(n.id()),
          vars(stack, n.label(), items.size(), removed));
      if (stack) items.removeLast();
      else items.removeFirst();
      removed.add(n.label());
      var after = retained(t, items, kind);
      t.add(
          after.nodes(),
          after.edges(),
          n.label() + " retirado",
          "Se conservan los demás elementos del lienzo.",
          "done",
          4,
          items.isEmpty() ? List.of() : List.of((stack ? items.getLast() : items.getFirst()).id()),
          vars(stack, n.label(), items.size(), removed));
    }
    return t.result(stack ? StackPopAlgorithm.CODE : QueueDequeueAlgorithm.CODE)
        .withJava(stack ? AlgorithmCode.stack() : AlgorithmCode.queue());
  }

  private static Map<String, String> vars(
      boolean stack, String value, int size, List<String> removed) {
    return Map.of(
        stack ? "tope" : "frente",
        value,
        "tamaño",
        "" + size,
        stack ? "retirados" : "atendidos",
        removed.toString());
  }

  private static SequenceScene.Scene retained(CanvasTrace t, List<Node3D> items, String kind) {
    List<Node3D> nodes = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      Node3D n = items.get(i);
      Map<String, Object> props = new LinkedHashMap<>(n.properties());
      props.put("index", i);
      props.put(
          "role",
          kind.equals("stack")
              ? (i == items.size() - 1 ? "top" : i == 0 ? "bottom" : "middle")
              : (i == 0 ? "front" : i == items.size() - 1 ? "rear" : "middle"));
      nodes.add(
          new Node3D(
              n.id(), n.label(), n.x(), n.y(), n.z(), n.depth(), n.parent(), Map.copyOf(props)));
    }
    Set<String> ids = new HashSet<>();
    nodes.forEach(n -> ids.add(n.id()));
    return new SequenceScene.Scene(
        List.copyOf(nodes),
        t.edges.stream().filter(e -> ids.contains(e.from()) && ids.contains(e.to())).toList());
  }
}
