package com.vista.pdg.service.generator.impl.graph;

import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TreeGenerator implements StructureGenerator<TreeContract> {

  @Override
  public String supportedType() {
    return "tree";
  }

  @Override
  public GeneratedStructure generate(TreeContract contract) {
    if (contract.nodes() != null && !contract.nodes().isEmpty()) return buildFromNodes(contract);

    return switch (contract.subtype()) {
      case "avl" -> buildAvl(contract);
      case "bst" -> buildBst(contract);
      case "heap" -> buildHeap(contract);
      default -> buildFromNodes(contract);
    };
  }

  // ── AVL ──────────────────────────────────────────────────────────────

  private GeneratedStructure buildAvl(TreeContract contract) {
    AvlNode root = null;
    if (contract.operations() != null) {
      for (TreeContract.Operation op : contract.operations()) {
        for (int v : op.values()) {
          if ("insert".equals(op.op())) root = avlInsert(root, v);
          else if ("delete".equals(op.op())) root = avlDelete(root, v);
        }
      }
    }
    return avlToStructure(contract, root);
  }

  private static class AvlNode {
    int value, height;
    AvlNode left, right;

    AvlNode(int v) {
      value = v;
      height = 1;
    }
  }

  private int height(AvlNode n) {
    return n == null ? 0 : n.height;
  }

  private int bf(AvlNode n) {
    return n == null ? 0 : height(n.left) - height(n.right);
  }

  private void updateHeight(AvlNode n) {
    n.height = 1 + Math.max(height(n.left), height(n.right));
  }

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

  private AvlNode avlInsert(AvlNode n, int v) {
    if (n == null) return new AvlNode(v);
    if (v < n.value) n.left = avlInsert(n.left, v);
    else if (v > n.value) n.right = avlInsert(n.right, v);
    return balance(n);
  }

  private AvlNode minNode(AvlNode n) {
    return n.left == null ? n : minNode(n.left);
  }

  private AvlNode avlDelete(AvlNode n, int v) {
    if (n == null) return null;
    if (v < n.value) n.left = avlDelete(n.left, v);
    else if (v > n.value) n.right = avlDelete(n.right, v);
    else {
      if (n.left == null) return n.right;
      if (n.right == null) return n.left;
      AvlNode m = minNode(n.right);
      n.value = m.value;
      n.right = avlDelete(n.right, m.value);
    }
    return balance(n);
  }

  private GeneratedStructure avlToStructure(TreeContract contract, AvlNode root) {
    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();
    int[] idx = {0};
    avlDfs(root, null, 0, nodes, edges, idx);
    return new GeneratedStructure(contract, nodes, edges, Map.of());
  }

  private void avlDfs(
      AvlNode n, String parentId, int depth, List<Node3D> nodes, List<Edge3D> edges, int[] idx) {
    if (n == null) return;
    String id = "n" + idx[0]++;
    nodes.add(
        new Node3D(
            id,
            String.valueOf(n.value),
            0,
            0,
            0,
            depth,
            parentId,
            Map.of("balanceFactor", bf(n), "height", n.height)));
    if (parentId != null) edges.add(new Edge3D("e" + edges.size(), parentId, id, null, true));
    avlDfs(n.left, id, depth + 1, nodes, edges, idx);
    avlDfs(n.right, id, depth + 1, nodes, edges, idx);
  }

  // ── BST ──────────────────────────────────────────────────────────────

  private GeneratedStructure buildBst(TreeContract contract) {
    AvlNode root = null;
    if (contract.operations() != null) {
      for (TreeContract.Operation op : contract.operations()) {
        for (int v : op.values()) {
          if ("insert".equals(op.op())) root = bstInsert(root, v);
        }
      }
    }
    return avlToStructure(contract, root);
  }

  private AvlNode bstInsert(AvlNode n, int v) {
    if (n == null) return new AvlNode(v);
    if (v < n.value) n.left = bstInsert(n.left, v);
    else if (v > n.value) n.right = bstInsert(n.right, v);
    return n;
  }

  // ── Heap ─────────────────────────────────────────────────────────────

  private GeneratedStructure buildHeap(TreeContract contract) {
    List<Integer> heap = new ArrayList<>();
    if (contract.operations() != null) {
      for (TreeContract.Operation op : contract.operations()) {
        for (int v : op.values()) {
          if ("insert".equals(op.op())) heapInsert(heap, v);
          else if ("delete".equals(op.op())) heapDelete(heap);
        }
      }
    }
    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();
    for (int i = 0; i < heap.size(); i++) {
      int depth = (int) (Math.log(i + 1) / Math.log(2));
      String pId = i > 0 ? "n" + ((i - 1) / 2) : null;
      nodes.add(new Node3D("n" + i, String.valueOf(heap.get(i)), 0, 0, 0, depth, pId, Map.of()));
      if (pId != null) edges.add(new Edge3D("e" + edges.size(), pId, "n" + i, null, true));
    }
    return new GeneratedStructure(contract, nodes, edges, Map.of());
  }

  private void heapInsert(List<Integer> heap, int v) {
    heap.add(v);
    int i = heap.size() - 1;
    while (i > 0) {
      int parent = (i - 1) / 2;
      if (heap.get(parent) < heap.get(i)) {
        Collections.swap(heap, parent, i);
        i = parent;
      } else break;
    }
  }

  private void heapDelete(List<Integer> heap) {
    if (heap.isEmpty()) return;
    heap.set(0, heap.get(heap.size() - 1));
    heap.remove(heap.size() - 1);
    int i = 0;
    while (true) {
      int l = 2 * i + 1, r = 2 * i + 2, largest = i;
      if (l < heap.size() && heap.get(l) > heap.get(largest)) largest = l;
      if (r < heap.size() && heap.get(r) > heap.get(largest)) largest = r;
      if (largest == i) break;
      Collections.swap(heap, i, largest);
      i = largest;
    }
  }

  // ── Pre-built nodes ───────────────────────────────────────────────────

  private GeneratedStructure buildFromNodes(TreeContract contract) {
    if (contract.nodes() == null)
      return new GeneratedStructure(contract, List.of(), List.of(), Map.of());

    // First pass: compute depths (parent is guaranteed to appear before child in the list,
    // but we resolve iteratively to handle any ordering).
    Map<String, String> parentOf = new HashMap<>();
    for (TreeContract.NodeDef nd : contract.nodes())
      if (nd.parent() != null) parentOf.put(nd.id(), nd.parent());

    Map<String, Integer> depthMap = new HashMap<>();
    for (TreeContract.NodeDef nd : contract.nodes()) {
      int depth = 0;
      String cur = nd.id();
      while (parentOf.containsKey(cur)) {
        cur = parentOf.get(cur);
        depth++;
      }
      depthMap.put(nd.id(), depth);
    }

    // Second pass: build nodes and edges
    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();
    for (TreeContract.NodeDef nd : contract.nodes()) {
      nodes.add(
          new Node3D(
              nd.id(),
              String.valueOf(nd.value()),
              0,
              0,
              0,
              depthMap.get(nd.id()),
              nd.parent(),
              Map.of()));
      if (nd.parent() != null)
        edges.add(new Edge3D("e" + edges.size(), nd.parent(), nd.id(), null, true));
    }
    return new GeneratedStructure(contract, nodes, edges, Map.of());
  }
}
