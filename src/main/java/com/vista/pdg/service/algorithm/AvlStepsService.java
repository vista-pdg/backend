package com.vista.pdg.service.algorithm;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AvlStepsService {

  private final TreeLayout3D treeLayout;

  public AvlStepsService(TreeLayout3D treeLayout) {
    this.treeLayout = treeLayout;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  public StepsResponse generateAvlInsertSteps(List<Integer> values) {
    if (values == null || values.isEmpty()) {
      return StepsResponse.error("Se requiere al menos un valor para la demostración");
    }

    List<AlgorithmStep> steps = new ArrayList<>();
    int[] idx = {0};
    AvlNode root = null;

    steps.add(makeStep(idx[0]++, "Árbol AVL vacío",
        "Estado inicial. Se insertarán los valores: " + values,
        "initial", List.of(), null, null));

    for (int v : values) {
      List<RotationEvent> rotations = new ArrayList<>();
      avlInsertTracked(deepClone(root), v, rotations);

      if (rotations.isEmpty()) {
        root = avlInsert(root, v);
        steps.add(makeStep(idx[0]++, "Insertar " + v,
            "Nodo " + v + " insertado como hoja. El árbol permanece balanceado: no se requieren rotaciones.",
            "insert", List.of(nid(v)), null, root));
      } else {
        AvlNode bstState = bstInsert(deepClone(root), v);
        steps.add(makeStep(idx[0]++, "Insertar " + v + " (BST)",
            "Nodo " + v + " colocado según la regla BST. El árbol puede haber quedado desbalanceado.",
            "insert", List.of(nid(v)), null, bstState));

        AvlNode working = deepClone(bstState);
        updateAllHeights(working);

        for (RotationEvent re : rotations) {
          steps.add(makeStep(idx[0]++, "Desbalance en nodo " + re.pivotValue(),
              "Factor de balance de nodo " + re.pivotValue() + " = " + re.bf()
                  + ". " + re.description() + ".",
              "unbalanced", List.of(nid(re.pivotValue())), re.rotationType(), working));

          working = applyRotationAt(working, re.pivotValue(), re.direction());
          updateAllHeights(working);

          steps.add(makeStep(idx[0]++, re.description(),
              "Nodo " + re.newRootValue() + " es la nueva raíz del subárbol. Factor de balance restaurado.",
              "rotated", List.of(nid(re.newRootValue())), null, working));
        }

        root = avlInsert(root, v);
        steps.add(makeStep(idx[0]++, v + " insertado — árbol balanceado",
            "Todas las rotaciones completadas. |BF| ≤ 1 para todos los nodos.",
            "balanced", List.of(nid(v)), null, root));
      }
    }

    return StepsResponse.ok(steps);
  }

  // ── Snapshot ──────────────────────────────────────────────────────────────

  private String nid(int value) {
    return "node-" + value;
  }

  private AlgorithmStep makeStep(int index, String title, String description,
      String highlightType, List<String> highlightedIds, String rotationType, AvlNode root) {

    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();

    if (root != null) {
      buildSnapshot(root, null, 0, nodes, edges);
      Map<String, Vec3> positions = treeLayout.compute(
          new GeneratedStructure(null, nodes, edges, Map.of()));
      nodes = nodes.stream()
          .map(n -> {
            Vec3 p = positions.getOrDefault(n.id(), Vec3.zero());
            return n.withPosition(p.x(), p.y(), p.z());
          })
          .toList();
    }

    return new AlgorithmStep(index, title, description, highlightType,
        highlightedIds, rotationType, nodes, edges);
  }

  private void buildSnapshot(AvlNode n, String parentId, int depth,
      List<Node3D> nodes, List<Edge3D> edges) {
    if (n == null) return;
    String id = nid(n.value);
    nodes.add(new Node3D(id, String.valueOf(n.value), 0, 0, 0, depth, parentId,
        Map.of("balanceFactor", bf(n), "height", n.height)));
    if (parentId != null)
      edges.add(new Edge3D("edge-" + parentId + "-" + id, parentId, id, null, true));
    buildSnapshot(n.left, id, depth + 1, nodes, edges);
    buildSnapshot(n.right, id, depth + 1, nodes, edges);
  }

  // ── Regular AVL insert ────────────────────────────────────────────────────

  private AvlNode avlInsert(AvlNode n, int v) {
    if (n == null) return new AvlNode(v);
    if (v < n.value) n.left = avlInsert(n.left, v);
    else if (v > n.value) n.right = avlInsert(n.right, v);
    return balance(n);
  }

  private AvlNode balance(AvlNode n) {
    updateHeight(n);
    int b = bf(n);
    if (b > 1) {
      if (bf(n.left) < 0) n.left = rotateLeft(n.left);
      return rotateRight(n);
    }
    if (b < -1) {
      if (bf(n.right) > 0) n.right = rotateRight(n.right);
      return rotateLeft(n);
    }
    return n;
  }

  // ── Tracked AVL insert (collects rotation events) ─────────────────────────

  private AvlNode avlInsertTracked(AvlNode n, int v, List<RotationEvent> events) {
    if (n == null) return new AvlNode(v);
    if (v < n.value) n.left = avlInsertTracked(n.left, v, events);
    else if (v > n.value) n.right = avlInsertTracked(n.right, v, events);
    return balanceTracked(n, events);
  }

  private AvlNode balanceTracked(AvlNode n, List<RotationEvent> events) {
    updateHeight(n);
    int b = bf(n);
    if (b > 1) {
      boolean isLR = bf(n.left) < 0;
      if (isLR) {
        int p1 = n.left.value;
        int r1 = n.left.right.value;
        events.add(new RotationEvent("left", p1, bf(n.left), r1,
            "Rotación Izquierda en nodo " + p1 + " (caso LR, paso 1/2)", "left-right"));
        n.left = rotateLeft(n.left);
      }
      int newRoot = n.left.value;
      events.add(new RotationEvent("right", n.value, b, newRoot,
          isLR
              ? "Rotación Derecha en nodo " + n.value + " (caso LR, paso 2/2)"
              : "Rotación Derecha en nodo " + n.value + " (caso LL)",
          isLR ? "left-right" : "right"));
      return rotateRight(n);
    }
    if (b < -1) {
      boolean isRL = bf(n.right) > 0;
      if (isRL) {
        int p1 = n.right.value;
        int r1 = n.right.left.value;
        events.add(new RotationEvent("right", p1, bf(n.right), r1,
            "Rotación Derecha en nodo " + p1 + " (caso RL, paso 1/2)", "right-left"));
        n.right = rotateRight(n.right);
      }
      int newRoot = n.right.value;
      events.add(new RotationEvent("left", n.value, b, newRoot,
          isRL
              ? "Rotación Izquierda en nodo " + n.value + " (caso RL, paso 2/2)"
              : "Rotación Izquierda en nodo " + n.value + " (caso RR)",
          isRL ? "right-left" : "left"));
      return rotateLeft(n);
    }
    return n;
  }

  // ── BST insert (no balancing) ─────────────────────────────────────────────

  private AvlNode bstInsert(AvlNode n, int v) {
    if (n == null) return new AvlNode(v);
    if (v < n.value) n.left = bstInsert(n.left, v);
    else if (v > n.value) n.right = bstInsert(n.right, v);
    return n;
  }

  // ── Rotation replay ───────────────────────────────────────────────────────

  private AvlNode applyRotationAt(AvlNode root, int pivotValue, String direction) {
    if (root == null) return null;
    if (root.value == pivotValue)
      return "left".equals(direction) ? rotateLeft(root) : rotateRight(root);
    if (pivotValue < root.value)
      root.left = applyRotationAt(root.left, pivotValue, direction);
    else
      root.right = applyRotationAt(root.right, pivotValue, direction);
    return root;
  }

  // ── Tree helpers ──────────────────────────────────────────────────────────

  private AvlNode rotateRight(AvlNode y) {
    AvlNode x = y.left, t = x.right;
    x.right = y;
    y.left = t;
    updateHeight(y);
    updateHeight(x);
    return x;
  }

  private AvlNode rotateLeft(AvlNode x) {
    AvlNode y = x.right, t = y.left;
    y.left = x;
    x.right = t;
    updateHeight(x);
    updateHeight(y);
    return y;
  }

  private int height(AvlNode n) {
    return n == null ? 0 : n.height;
  }

  private int bf(AvlNode n) {
    return n == null ? 0 : height(n.left) - height(n.right);
  }

  private void updateHeight(AvlNode n) {
    if (n != null) n.height = 1 + Math.max(height(n.left), height(n.right));
  }

  private void updateAllHeights(AvlNode n) {
    if (n == null) return;
    updateAllHeights(n.left);
    updateAllHeights(n.right);
    updateHeight(n);
  }

  private AvlNode deepClone(AvlNode n) {
    if (n == null) return null;
    AvlNode c = new AvlNode(n.value);
    c.height = n.height;
    c.left = deepClone(n.left);
    c.right = deepClone(n.right);
    return c;
  }

  // ── Inner types ───────────────────────────────────────────────────────────

  private static class AvlNode {
    int value, height;
    AvlNode left, right;

    AvlNode(int v) {
      value = v;
      height = 1;
    }
  }

  private record RotationEvent(
      String direction,
      int pivotValue,
      int bf,
      int newRootValue,
      String description,
      String rotationType) {}
}
