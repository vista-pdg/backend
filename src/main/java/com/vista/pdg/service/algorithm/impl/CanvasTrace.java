package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import java.util.*;

/** Validated, request-local scene; never mutates the caller's nodes or properties. */
final class CanvasTrace {
  final List<Node3D> nodes;
  final List<Edge3D> edges;
  final Map<String, Node3D> byId = new LinkedHashMap<>();
  final List<AlgorithmStep> steps = new ArrayList<>();
  final Deque<AlgorithmStep.Frame> frames = new ArrayDeque<>();

  CanvasTrace(AlgorithmRequest r) {
    this(r.nodes(), r.edges());
  }

  CanvasTrace(List<Node3D> inputNodes, List<Edge3D> inputEdges) {
    if (inputNodes == null || inputNodes.isEmpty())
      throw new InvalidAlgorithmInputException("Genera primero la estructura en el lienzo.");
    if (inputNodes.size() > 64)
      throw new InvalidAlgorithmInputException("Usa hasta 64 nodos para esta demostración.");
    nodes = List.copyOf(inputNodes);
    edges = inputEdges == null ? List.of() : List.copyOf(inputEdges);
    for (Node3D n : nodes) {
      if (n.id() == null || n.id().isBlank() || n.label() == null || byId.put(n.id(), n) != null)
        throw new InvalidAlgorithmInputException("Los nodos requieren IDs únicos y etiquetas.");
    }
    for (Edge3D e : edges) {
      if (!byId.containsKey(e.from()) || !byId.containsKey(e.to()))
        throw new InvalidAlgorithmInputException("Una arista apunta fuera de la estructura.");
    }
  }

  static List<Integer> values(AlgorithmRequest r, int limit) {
    if (r.values() == null || r.values().isEmpty())
      throw new InvalidAlgorithmInputException("Ingresa al menos un entero.", "values");
    if (r.values().size() > limit || r.values().stream().anyMatch(Objects::isNull))
      throw new InvalidAlgorithmInputException(
          "Ingresa hasta " + limit + " enteros de 32 bits.", "values");
    return List.copyOf(r.values());
  }

  static int argument(AlgorithmRequest r) {
    Double v = r.argument();
    if (v == null
        || !Double.isFinite(v)
        || v != Math.rint(v)
        || v < Integer.MIN_VALUE
        || v > Integer.MAX_VALUE)
      throw new InvalidAlgorithmInputException("Ingresa un valor entero de 32 bits.", "argument");
    return v.intValue();
  }

  static int value(Node3D n) {
    try {
      return Integer.parseInt(n.label());
    } catch (NumberFormatException ex) {
      throw new InvalidAlgorithmInputException(
          "La estructura debe contener etiquetas enteras de 32 bits.");
    }
  }

  void add(
      String title,
      String description,
      String type,
      int line,
      List<String> active,
      Map<String, String> vars) {
    add(nodes, edges, title, description, type, line, active, vars);
  }

  void add(
      List<Node3D> ns,
      List<Edge3D> es,
      String title,
      String description,
      String type,
      int line,
      List<String> active,
      Map<String, String> vars) {
    var snapshot =
        ns.stream()
            .map(
                n -> {
                  Map<String, Object> p = new LinkedHashMap<>();
                  if (n.properties() != null) p.putAll(n.properties());
                  p.put("state", active.contains(n.id()) ? "current" : "unvisited");
                  return new Node3D(
                      n.id(), n.label(), n.x(), n.y(), n.z(), n.depth(), n.parent(), Map.copyOf(p));
                })
            .toList();
    steps.add(
        new AlgorithmStep(
            steps.size(),
            title,
            description,
            type,
            List.copyOf(active),
            null,
            snapshot,
            List.copyOf(es),
            line,
            Map.copyOf(vars),
            List.copyOf(frames)));
  }

  StepsResponse result(List<String> code) {
    return StepsResponse.ok(List.copyOf(steps), code, "pseudocode");
  }
}
