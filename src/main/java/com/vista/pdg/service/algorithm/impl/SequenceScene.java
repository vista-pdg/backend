package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.service.layout.impl.*;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.*;

/**
 * Stable element IDs; canonical positions and links are rebuilt only when an operation changes
 * order.
 */
final class SequenceScene {
  record Scene(List<Node3D> nodes, List<Edge3D> edges) {}

  static List<Node3D> fromValues(List<Integer> values) {
    List<Node3D> out = new ArrayList<>();
    for (int i = 0; i < values.size(); i++)
      out.add(new Node3D("n" + i, "" + values.get(i), 0, 0, 0, 0, null, Map.of("index", i)));
    return out;
  }

  static Scene place(List<Node3D> items, String kind, boolean doubly, boolean circular) {
    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      Node3D n = items.get(i);
      Map<String, Object> props = new LinkedHashMap<>();
      if (n.properties() != null) props.putAll(n.properties());
      props.remove("role");
      props.remove("sequence");
      props.remove("listSubtype");
      props.put("index", i);
      if (kind.equals("list")) {
        props.put("sequence", true);
        props.put("listSubtype", circular ? "circular" : doubly ? "doubly" : "singly");
      }
      if (kind.equals("stack"))
        props.put("role", i == items.size() - 1 ? "top" : i == 0 ? "bottom" : "middle");
      if (kind.equals("queue"))
        props.put("role", i == 0 ? "front" : i == items.size() - 1 ? "rear" : "middle");
      boolean heap = kind.equals("heap");
      String parent = heap && i > 0 ? items.get((i - 1) / 2).id() : null;
      int depth = heap ? 31 - Integer.numberOfLeadingZeros(i + 1) : 0;
      nodes.add(
          new Node3D(n.id(), n.label(), n.x(), n.y(), n.z(), depth, parent, Map.copyOf(props)));
      if (parent != null) link(edges, parent, n.id());
      else if (!heap && !kind.equals("stack") && i > 0) {
        link(edges, items.get(i - 1).id(), n.id());
        if (doubly) link(edges, n.id(), items.get(i - 1).id());
      }
    }
    if (circular && items.size() > 1) link(edges, items.getLast().id(), items.getFirst().id());
    GeneratedStructure s = new GeneratedStructure(null, nodes, edges, Map.of());
    var pos =
        kind.equals("stack")
            ? new StackLayout3D().compute(s)
            : kind.equals("heap") ? new TreeLayout3D().compute(s) : new LinearLayout3D().compute(s);
    return new Scene(
        nodes.stream()
            .map(
                n -> {
                  var p = pos.get(n.id());
                  return n.withPosition(p.x(), p.y(), p.z());
                })
            .toList(),
        List.copyOf(edges));
  }

  private static void link(List<Edge3D> edges, String from, String to) {
    edges.add(new Edge3D("link:" + from + ":" + to, from, to, null, true));
  }

  static void validateListLinks(
      CanvasTrace t, List<Node3D> items, boolean doubly, boolean circular) {
    Set<List<String>> expected = new HashSet<>();
    for (int i = 1; i < items.size(); i++) {
      expected.add(List.of(items.get(i - 1).id(), items.get(i).id()));
      if (doubly) expected.add(List.of(items.get(i).id(), items.get(i - 1).id()));
    }
    if (circular && items.size() > 1)
      expected.add(List.of(items.getLast().id(), items.getFirst().id()));
    Set<List<String>> actual = new HashSet<>();
    for (Edge3D e : t.edges) {
      if (!e.directed() || !actual.add(List.of(e.from(), e.to())))
        throw new InvalidAlgorithmInputException("Los enlaces de la lista son inconsistentes.");
    }
    if (!expected.equals(actual))
      throw new InvalidAlgorithmInputException(
          "Los enlaces no coinciden con el orden de la lista.");
  }

  static List<Node3D> ordered(CanvasTrace t) {
    if (t.nodes.stream()
        .allMatch(n -> n.properties() != null && n.properties().get("index") instanceof Number)) {
      Set<Integer> indexes = new HashSet<>();
      for (Node3D n : t.nodes) {
        double i = ((Number) n.properties().get("index")).doubleValue();
        if (i < 0 || i != Math.rint(i) || i >= t.nodes.size() || !indexes.add((int) i))
          throw new InvalidAlgorithmInputException(
              "Los índices de la secuencia deben ser únicos y consecutivos.");
      }
      return new ArrayList<>(
          t.nodes.stream()
              .sorted(
                  Comparator.comparingInt(n -> ((Number) n.properties().get("index")).intValue()))
              .toList());
    }
    if (t.nodes.size() == 1 && t.edges.isEmpty()) return new ArrayList<>(t.nodes);
    Map<String, List<String>> out = new LinkedHashMap<>();
    Map<String, Integer> in = new HashMap<>();
    t.nodes.forEach(
        n -> {
          out.put(n.id(), new ArrayList<>());
          in.put(n.id(), 0);
        });
    for (Edge3D e : t.edges) {
      if (!e.directed() || e.from().equals(e.to()))
        throw new InvalidAlgorithmInputException(
            "Se requiere una lista con enlaces dirigidos válidos.");
      if (!out.get(e.from()).contains(e.to())) {
        out.get(e.from()).add(e.to());
        in.merge(e.to(), 1, Integer::sum);
      }
    }
    boolean doubly = t.edges.stream().anyMatch(e -> out.get(e.to()).contains(e.from()));
    String head;
    if (doubly) {
      if (t.edges.stream().anyMatch(e -> !out.get(e.to()).contains(e.from()))
          || out.values().stream().anyMatch(k -> k.size() > 2))
        throw new InvalidAlgorithmInputException(
            "Los enlaces de la lista doble son inconsistentes.");
      var ends = t.nodes.stream().filter(n -> out.get(n.id()).size() == 1).toList();
      if (ends.size() != 2)
        throw new InvalidAlgorithmInputException("La lista doble debe tener cabeza y cola.");
      head =
          ends.stream()
              .filter(n -> n.id().equals(t.nodes.getFirst().id()))
              .findFirst()
              .orElseGet(
                  () -> ends.stream().min(Comparator.comparingDouble(Node3D::x)).orElseThrow())
              .id();
    } else {
      if (out.values().stream().anyMatch(k -> k.size() > 1)
          || in.values().stream().anyMatch(i -> i > 1))
        throw new InvalidAlgorithmInputException(
            "La estructura no es una lista lineal o circular simple.");
      var heads = t.nodes.stream().filter(n -> in.get(n.id()) == 0).toList();
      if (heads.size() > 1) throw new InvalidAlgorithmInputException("La lista está desconectada.");
      head = heads.isEmpty() ? t.nodes.getFirst().id() : heads.getFirst().id();
    }
    List<Node3D> ordered = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String id = head, previous = null;
    while (id != null && !seen.contains(id)) {
      seen.add(id);
      ordered.add(t.byId.get(id));
      String prev = previous;
      String next = out.get(id).stream().filter(k -> !k.equals(prev)).findFirst().orElse(null);
      previous = id;
      id = next;
    }
    if (ordered.size() != t.nodes.size() || (id != null && !id.equals(head)))
      throw new InvalidAlgorithmInputException(
          "La lista contiene un ciclo parcial o nodos desconectados.");
    return ordered;
  }

  static String freshId(List<Node3D> nodes) {
    Set<String> ids = new HashSet<>();
    nodes.forEach(n -> ids.add(n.id()));
    String id = "added";
    for (int i = 1; ids.contains(id); i++) id = "added-" + i;
    return id;
  }
}
