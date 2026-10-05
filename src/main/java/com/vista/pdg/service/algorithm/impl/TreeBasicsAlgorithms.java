package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Shared instrumentation with independently registered strategies for each tree contract. */
public final class TreeBasicsAlgorithms {
  private TreeBasicsAlgorithms() {}

  abstract static class Walk implements AlgorithmStrategy {
    private final AlgorithmDescriptor descriptor;
    private final String order;

    Walk(String subtype, String order, String label) {
      this.order = order;
      descriptor =
          new AlgorithmDescriptor(
              "tree",
              subtype,
              order,
              "tree",
              label,
              "Sobre el árbol del lienzo; conserva nodos, aristas y posiciones",
              "structure");
    }

    public AlgorithmDescriptor descriptor() {
      return descriptor;
    }

    public StepsResponse generate(AlgorithmRequest r) {
      CanvasTrace t = new CanvasTrace(r);
      TreeIndex tree = new TreeIndex(t);
      if (!descriptor.subtype().equals("btree")) tree.requireBst();
      List<String> code =
          order.equals("levelorder")
              ? List.of(
                  "recorrer_por_niveles(raíz):",
                  "  cola ← [raíz]",
                  "  mientras cola no esté vacía:",
                  "    nodo ← retirar_frente(cola); visitar(nodo)",
                  "    encolar sus hijos de izquierda a derecha",
                  "  retornar salida")
              : List.of(
                  order + "(nodo):",
                  "  si nodo es nulo: retornar",
                  "  visitar antes de los hijos si es preorden",
                  "  recorrer cada hijo de izquierda a derecha",
                  "  visitar después de los hijos si es postorden",
                  "  retornar salida");
      List<String> output = new ArrayList<>();
      t.add(
          "Inicio del recorrido",
          "Raíz: " + t.byId.get(tree.root).label(),
          "initial",
          1,
          List.of(),
          Map.of("salida", "[]"));
      if (order.equals("levelorder")) {
        Deque<String> q = new ArrayDeque<>();
        q.add(tree.root);
        while (!q.isEmpty()) {
          String id = q.removeFirst();
          output.add(t.byId.get(id).label());
          q.addAll(tree.children.get(id));
          t.add(
              "Visitar " + t.byId.get(id).label(),
              "Se visita el siguiente nodo del nivel.",
              "visit",
              4,
              List.of(id),
              Map.of("salida", output.toString(), "pendientes", q.toString()));
        }
      } else dfs(tree, tree.root, output, t);
      t.add(
          "Recorrido completo",
          output.toString(),
          "done",
          6,
          List.of(),
          Map.of("salida", output.toString()));
      return t.result(code);
    }

    private void dfs(TreeIndex tree, String id, List<String> out, CanvasTrace t) {
      t.frames.addLast(new AlgorithmStep.Frame(order, Map.of("nodo", t.byId.get(id).label())));
      t.add(
          "Llamada a " + t.byId.get(id).label(),
          "Se recorre este subárbol.",
          "frontier",
          1,
          List.of(id),
          Map.of("salida", out.toString()));
      if (order.equals("preorder")) visit(id, out, t, 3);
      for (String k : tree.children.get(id)) dfs(tree, k, out, t);
      if (order.equals("postorder")) visit(id, out, t, 5);
      t.add(
          "Retornar desde " + t.byId.get(id).label(),
          "Subárbol recorrido.",
          "done",
          6,
          List.of(id),
          Map.of("salida", out.toString()));
      t.frames.removeLast();
    }

    private void visit(String id, List<String> out, CanvasTrace t, int line) {
      out.add(t.byId.get(id).label());
      t.add(
          "Visitar " + t.byId.get(id).label(),
          "Salida acumulada: " + out,
          "visit",
          line,
          List.of(id),
          Map.of("salida", out.toString()));
    }
  }

  abstract static class Search implements AlgorithmStrategy {
    private final AlgorithmDescriptor descriptor;

    Search(String subtype) {
      descriptor =
          new AlgorithmDescriptor(
              "tree",
              subtype,
              "search",
              "tree",
              "Búsqueda · Encontrar una clave",
              subtype.equals("btree")
                  ? "Recorre las claves del B-árbol del lienzo"
                  : "Compara la clave y sigue la rama menor o mayor",
              "structure",
              "target");
    }

    public AlgorithmDescriptor descriptor() {
      return descriptor;
    }

    public StepsResponse generate(AlgorithmRequest r) {
      int target = CanvasTrace.argument(r);
      CanvasTrace t = new CanvasTrace(r);
      TreeIndex tree = new TreeIndex(t);
      boolean binary = !descriptor.subtype().equals("btree");
      if (binary) tree.requireBst();
      else t.nodes.forEach(CanvasTrace::value);
      List<String> code =
          binary
              ? List.of(
                  "buscar(raíz, objetivo):",
                  "  nodo ← raíz",
                  "  mientras nodo no sea nulo:",
                  "    comparar objetivo con la clave del nodo",
                  "    si coincide: retornar nodo",
                  "    nodo ← hijo menor o mayor según la comparación",
                  "  retornar no_encontrado")
              : List.of(
                  "buscar_por_recorrido(raíz, objetivo):",
                  "  cola ← [raíz]",
                  "  mientras cola no esté vacía:",
                  "    nodo ← retirar_frente(cola); comparar su clave",
                  "    si coincide: retornar nodo",
                  "    encolar los hijos del nodo",
                  "  retornar no_encontrado");
      t.add(
          "Buscar " + target,
          "Se consulta el árbol sin modificarlo.",
          "initial",
          1,
          List.of(),
          Map.of("objetivo", "" + target));
      Deque<String> pending = new ArrayDeque<>();
      pending.add(tree.root);
      String found = null;
      while (!pending.isEmpty()) {
        String id = pending.removeFirst();
        int value = CanvasTrace.value(t.byId.get(id));
        t.add(
            "Comparar con " + value,
            target + " frente a " + value,
            "visit",
            4,
            List.of(id),
            Map.of("objetivo", "" + target, "clave", "" + value));
        if (target == value) {
          found = id;
          break;
        }
        if (binary) {
          tree.children.get(id).stream()
              .filter(k -> (CanvasTrace.value(t.byId.get(k)) < value) == (target < value))
              .findFirst()
              .ifPresent(pending::add);
        } else pending.addAll(tree.children.get(id));
      }
      t.add(
          found == null ? "Clave ausente" : "Clave encontrada",
          found == null ? target + " no está en el árbol." : "Nodo: " + found,
          "done",
          found == null ? 7 : 5,
          found == null ? List.of() : List.of(found),
          Map.of("encontrado", "" + (found != null), "objetivo", "" + target));
      return t.result(code);
    }
  }

  @Service
  public static class BstPreorder extends Walk {
    public BstPreorder() {
      super("bst", "preorder", "Preorden · Raíz, izquierda, derecha");
    }
  }

  @Service
  public static class BstPostorder extends Walk {
    public BstPostorder() {
      super("bst", "postorder", "Postorden · Izquierda, derecha, raíz");
    }
  }

  @Service
  public static class BstLevels extends Walk {
    public BstLevels() {
      super("bst", "levelorder", "Por niveles · Recorrido en anchura");
    }
  }

  @Service
  public static class AvlPreorder extends Walk {
    public AvlPreorder() {
      super("avl", "preorder", "Preorden · Recorrido del AVL");
    }
  }

  @Service
  public static class AvlPostorder extends Walk {
    public AvlPostorder() {
      super("avl", "postorder", "Postorden · Recorrido del AVL");
    }
  }

  @Service
  public static class AvlLevels extends Walk {
    public AvlLevels() {
      super("avl", "levelorder", "Por niveles · Recorrido del AVL");
    }
  }

  @Service
  public static class BtreeLevels extends Walk {
    public BtreeLevels() {
      super("btree", "levelorder", "Por niveles · Recorrido del B-árbol");
    }
  }

  @Service
  public static class BstSearch extends Search {
    public BstSearch() {
      super("bst");
    }
  }

  @Service
  public static class AvlSearch extends Search {
    public AvlSearch() {
      super("avl");
    }
  }

  @Service
  public static class BtreeSearch extends Search {
    public BtreeSearch() {
      super("btree");
    }
  }
}
