package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Recorrido inorden recursivo instrumentado (HU-22a).
 *
 * <p>Recorre el árbol que hay en el lienzo (nodos con {@code parent}) o, si la petición no trae
 * estructura, construye un BST con {@code values}. Emite <b>un paso por línea ejecutada</b> del
 * pseudocódigo: cada {@link AlgorithmStep} lleva {@code line} y el nodo sobre el que se ejecuta, de
 * modo que el panel de código y los adaptadores 2D/3D consumen el mismo rastro. El estado por nodo
 * va en {@code properties.state} ({@code current}, {@code visited}) como en BFS.
 */
@Service
public class InorderTraversalAlgorithm implements AlgorithmStrategy {

  public static final List<String> CODE =
      List.of(
          "inorden(nodo):",
          "  si nodo es nulo:",
          "    retornar",
          "  inorden(nodo.izq)",
          "  visitar(nodo)",
          "  inorden(nodo.der)",
          "  retornar");

  private static final AlgorithmDescriptor DESCRIPTOR =
      new AlgorithmDescriptor(
          "tree",
          "bst",
          "inorder",
          "tree",
          "Inorden · Recorrido recursivo",
          "Sobre el árbol del lienzo (o un BST con los valores dados); una línea de código por paso",
          "structure");

  private final TreeLayout3D layout;

  public InorderTraversalAlgorithm(TreeLayout3D layout) {
    this.layout = layout;
  }

  @Override
  public AlgorithmDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public StepsResponse generate(AlgorithmRequest req) {
    Tree tree =
        req.nodes() != null && !req.nodes().isEmpty()
            ? Tree.fromStructure(req.nodes(), req.edges())
            : Tree.fromValues(req.values());
    if (tree == null) {
      return StepsResponse.error("Inorden necesita un árbol en el lienzo o una lista de valores");
    }
    Tracer t = new Tracer(tree);
    t.step(
        1,
        tree.root,
        "frontier",
        "inorden(" + tree.label(tree.root) + ")",
        "Llamada inicial con la raíz.");
    t.inorder(tree.root, 1);
    t.done();
    return StepsResponse.ok(t.steps, CODE, "pseudocode");
  }

  // ── Árbol de trabajo ─────────────────────────────────────────────────────

  private static final class Tree {
    String root;
    final Map<String, String> left = new HashMap<>();
    final Map<String, String> right = new HashMap<>();
    final List<Node3D> nodes;
    final List<Edge3D> edges;
    final Map<String, Node3D> byId = new LinkedHashMap<>();

    Tree(List<Node3D> nodes, List<Edge3D> edges) {
      this.nodes = nodes;
      this.edges = edges;
      for (Node3D n : nodes) byId.put(n.id(), n);
    }

    String label(String id) {
      Node3D n = byId.get(id);
      return n == null ? id : n.label();
    }

    /**
     * Hijo menor a la izquierda, mayor a la derecha; sin etiquetas numéricas, por orden de arista.
     */
    static Tree fromStructure(List<Node3D> nodes, List<Edge3D> edges) {
      if (edges == null) edges = List.of();
      Tree t = new Tree(nodes, edges);
      Map<String, List<String>> children = new LinkedHashMap<>();
      for (Node3D n : nodes) children.put(n.id(), new ArrayList<>());
      java.util.Set<String> hasParent = new java.util.HashSet<>();
      for (Edge3D e : edges) {
        if (!children.containsKey(e.from()) || !children.containsKey(e.to())) continue;
        children.get(e.from()).add(e.to());
        hasParent.add(e.to());
      }
      t.root =
          nodes.stream()
              .map(Node3D::id)
              .filter(id -> !hasParent.contains(id))
              .findFirst()
              .orElse(null);
      if (t.root == null) return null;
      for (Map.Entry<String, List<String>> en : children.entrySet()) {
        List<String> kids = en.getValue();
        if (kids.size() > 2) return null;
        Double pv = numeric(t.label(en.getKey()));
        for (String k : kids) {
          Double kv = numeric(t.label(k));
          boolean goesLeft = pv != null && kv != null ? kv < pv : !t.left.containsKey(en.getKey());
          if (goesLeft && !t.left.containsKey(en.getKey())) t.left.put(en.getKey(), k);
          else if (!t.right.containsKey(en.getKey())) t.right.put(en.getKey(), k);
          else t.left.put(en.getKey(), k);
        }
      }
      return t;
    }

    static Tree fromValues(List<Integer> values) {
      if (values == null || values.isEmpty()) return null;
      Map<Integer, int[]> lr = new LinkedHashMap<>();
      Integer root = null;
      for (int v : values) {
        if (root == null) {
          root = v;
          lr.put(v, new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE});
          continue;
        }
        int cur = root;
        while (true) {
          int[] c = lr.get(cur);
          if (v == cur) break;
          int side = v < cur ? 0 : 1;
          if (c[side] == Integer.MIN_VALUE) {
            c[side] = v;
            lr.put(v, new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE});
            break;
          }
          cur = c[side];
        }
      }
      List<Node3D> nodes = new ArrayList<>();
      List<Edge3D> edges = new ArrayList<>();
      build(root, null, 0, lr, nodes, edges);
      Tree t = new Tree(nodes, edges);
      t.root = "node-" + root;
      for (Map.Entry<Integer, int[]> en : lr.entrySet()) {
        if (en.getValue()[0] != Integer.MIN_VALUE)
          t.left.put("node-" + en.getKey(), "node-" + en.getValue()[0]);
        if (en.getValue()[1] != Integer.MIN_VALUE)
          t.right.put("node-" + en.getKey(), "node-" + en.getValue()[1]);
      }
      return t;
    }

    private static void build(
        int v,
        String parent,
        int depth,
        Map<Integer, int[]> lr,
        List<Node3D> nodes,
        List<Edge3D> edges) {
      String id = "node-" + v;
      nodes.add(new Node3D(id, String.valueOf(v), 0, 0, 0, depth, parent, Map.of()));
      if (parent != null)
        edges.add(new Edge3D("edge-" + parent + "-" + id, parent, id, null, true));
      int[] c = lr.get(v);
      if (c[0] != Integer.MIN_VALUE) build(c[0], id, depth + 1, lr, nodes, edges);
      if (c[1] != Integer.MIN_VALUE) build(c[1], id, depth + 1, lr, nodes, edges);
    }

    private static Double numeric(String s) {
      try {
        return Double.valueOf(s.trim());
      } catch (RuntimeException e) {
        return null;
      }
    }
  }

  // ── Trazador ─────────────────────────────────────────────────────────────

  private final class Tracer {
    final Tree tree;
    final List<AlgorithmStep> steps = new ArrayList<>();
    final List<String> output = new ArrayList<>();
    final Map<String, String> state = new HashMap<>();
    final Map<String, Vec3> positions;

    Tracer(Tree tree) {
      this.tree = tree;
      boolean positioned =
          tree.nodes.stream().anyMatch(n -> n.x() != 0 || n.y() != 0 || n.z() != 0);
      this.positions =
          positioned
              ? Map.of()
              : layout.compute(new GeneratedStructure(null, tree.nodes, tree.edges, Map.of()));
    }

    void inorder(String node, int depth) {
      step(
          2,
          node,
          "frontier",
          "¿" + (node == null ? "nulo" : tree.label(node)) + " es nulo?",
          node == null
              ? "Sí: no hay nada que recorrer."
              : "No: se sigue con el subárbol izquierdo.");
      if (node == null) {
        step(3, null, "frontier", "retornar", "Se vuelve al llamador.");
        return;
      }
      state.put(node, "current");
      String l = tree.left.get(node);
      step(
          4,
          node,
          "frontier",
          "inorden(" + (l == null ? "nulo" : tree.label(l)) + ")",
          "Llamada recursiva con el hijo izquierdo de " + tree.label(node) + ".");
      inorder(l, depth + 1);
      state.put(node, "current");
      output.add(tree.label(node));
      step(
          5,
          node,
          "visit",
          "visitar(" + tree.label(node) + ")",
          "Se emite " + tree.label(node) + ". Salida: " + output);
      state.put(node, "visited");
      String r = tree.right.get(node);
      step(
          6,
          node,
          "frontier",
          "inorden(" + (r == null ? "nulo" : tree.label(r)) + ")",
          "Llamada recursiva con el hijo derecho de " + tree.label(node) + ".");
      inorder(r, depth + 1);
      step(7, node, "done", "retornar", "Termina inorden(" + tree.label(node) + ").");
    }

    void done() {
      AlgorithmStep last = steps.getLast();
      steps.add(
          new AlgorithmStep(
              steps.size(),
              "Recorrido completo",
              "Secuencia inorden: " + output,
              "done",
              tree.nodes.stream().map(Node3D::id).toList(),
              null,
              last.nodes(),
              last.edges(),
              null));
    }

    void step(int line, String node, String type, String title, String description) {
      List<Node3D> snapshot =
          tree.nodes.stream()
              .map(
                  n -> {
                    Map<String, Object> props =
                        new LinkedHashMap<>(n.properties() == null ? Map.of() : n.properties());
                    props.put("state", state.getOrDefault(n.id(), "unvisited"));
                    Vec3 p = positions.get(n.id());
                    Node3D placed = p == null ? n : n.withPosition(p.x(), p.y(), p.z());
                    return new Node3D(
                        placed.id(),
                        placed.label(),
                        placed.x(),
                        placed.y(),
                        placed.z(),
                        placed.depth(),
                        placed.parent(),
                        props);
                  })
              .toList();
      steps.add(
          new AlgorithmStep(
              steps.size(),
              title,
              description,
              type,
              node == null ? List.of() : List.of(node),
              null,
              snapshot,
              tree.edges,
              line));
    }
  }
}
