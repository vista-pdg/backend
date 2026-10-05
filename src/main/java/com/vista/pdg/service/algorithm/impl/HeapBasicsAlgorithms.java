package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

public final class HeapBasicsAlgorithms {
  private HeapBasicsAlgorithms() {}

  abstract static class Operation implements AlgorithmStrategy {
    private final String operation;

    Operation(String operation) {
      this.operation = operation;
    }

    public AlgorithmDescriptor descriptor() {
      return new AlgorithmDescriptor(
          "tree",
          "heap",
          operation,
          "heap",
          switch (operation) {
            case "heapify" -> "Heapify · Construir heap máximo";
            case "insert" -> "Inserción · Subir hacia la raíz";
            case "extract" -> "Extraer máximo · Hundir la raíz";
            default -> "Máximo · Consultar la raíz";
          },
          operation.equals("heapify")
              ? "Construcción de abajo hacia arriba en O(n)"
              : "Sobre el heap máximo del lienzo",
          "heapify".equals(operation) ? "values" : "structure",
          operation.equals("insert") ? "value" : null);
    }

    public StepsResponse generate(AlgorithmRequest r) {
      List<Node3D> heap;
      CanvasTrace t;
      if (operation.equals("heapify")) {
        heap = SequenceScene.fromValues(CanvasTrace.values(r, 64));
        var scene = SequenceScene.place(heap, "heap", false, false);
        t = new CanvasTrace(scene.nodes(), scene.edges());
      } else {
        t = new CanvasTrace(r);
        TreeIndex tree = new TreeIndex(t);
        heap = new ArrayList<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(tree.root);
        boolean gap = false;
        while (!q.isEmpty()) {
          String id = q.removeFirst();
          List<String> kids = tree.children.get(id);
          if (kids.size() > 2 || (gap && !kids.isEmpty()))
            throw new InvalidAlgorithmInputException("El heap debe ser un árbol binario completo.");
          if (kids.size() < 2) gap = true;
          heap.add(t.byId.get(id));
          q.addAll(kids);
        }
        for (int i = 1; i < heap.size(); i++)
          if (CanvasTrace.value(heap.get((i - 1) / 2)) < CanvasTrace.value(heap.get(i)))
            throw new InvalidAlgorithmInputException(
                "Se requiere un heap máximo; usa Heapify para construirlo.");
      }
      heap.forEach(CanvasTrace::value);
      var code =
          switch (operation) {
            case "heapify" ->
                List.of(
                    "heapify(valores):",
                    "  representar como árbol completo",
                    "  desde el último padre hasta la raíz:",
                    "    comparar con el hijo mayor",
                    "    intercambiar y continuar hundiendo si hace falta",
                    "  retornar heap máximo");
            case "insert" ->
                List.of(
                    "insertar(heap, valor):",
                    "  añadir una hoja al final",
                    "  mientras el valor supere a su padre:",
                    "    comparar con el padre",
                    "    intercambiar y continuar subiendo",
                    "  retornar heap máximo");
            case "extract" ->
                List.of(
                    "extraer_máximo(heap):",
                    "  guardar la raíz como máximo",
                    "  sustituir raíz por última hoja y retirarla",
                    "  comparar con el hijo mayor",
                    "  intercambiar y continuar hundiendo si hace falta",
                    "  retornar máximo y heap");
            default -> List.of("máximo(heap):", "  retornar raíz sin retirarla");
          };
      t.add(
          "Estado inicial",
          operation.equals("heapify")
              ? "Construcción desde un árbol completo."
              : "Heap máximo del lienzo, sin modificarlo.",
          "initial",
          1,
          List.of(),
          Map.of("tamaño", "" + heap.size()));
      String result = "";
      switch (operation) {
        case "heapify" -> {
          for (int i = heap.size() / 2 - 1; i >= 0; i--) sink(t, heap, i);
        }
        case "insert" -> {
          if (heap.size() >= 64)
            throw new InvalidAlgorithmInputException("El heap admite hasta 64 nodos.");
          int value = CanvasTrace.argument(r);
          Node3D n =
              new Node3D(SequenceScene.freshId(heap), "" + value, 0, 0, 0, 0, null, Map.of());
          heap.add(n);
          int i = heap.size() - 1;
          snapshot(
              t,
              heap,
              "Añadir " + value,
              "La hoja se coloca al final del árbol completo.",
              "insert",
              2,
              List.of(n.id()),
              Map.of("valor", "" + value));
          while (i > 0) {
            int p = (i - 1) / 2;
            snapshot(
                t,
                heap,
                "Comparar con padre",
                "Se verifica la propiedad del heap.",
                "visit",
                4,
                List.of(heap.get(i).id(), heap.get(p).id()),
                Map.of("índice", "" + i, "padre", "" + p));
            if (CanvasTrace.value(heap.get(p)) >= value) break;
            Collections.swap(heap, i, p);
            snapshot(
                t,
                heap,
                "Subir " + value,
                "Se intercambia con el padre.",
                "rotated",
                5,
                List.of(n.id()),
                Map.of("índice", "" + p));
            i = p;
          }
        }
        case "extract" -> {
          result = heap.getFirst().label();
          Node3D last = heap.removeLast();
          if (!heap.isEmpty()) heap.set(0, last);
          snapshot(
              t,
              heap,
              "Extraer " + result,
              "La última hoja sustituye a la raíz.",
              "pop",
              3,
              heap.isEmpty() ? List.of() : List.of(last.id()),
              Map.of("máximo", result));
          if (!heap.isEmpty()) sink(t, heap, 0);
        }
        default -> {
          t.add(
              "Máximo: " + heap.getFirst().label(),
              "La raíz permanece en el heap.",
              "done",
              2,
              List.of(heap.getFirst().id()),
              Map.of("máximo", heap.getFirst().label()));
          return t.result(code);
        }
      }
      snapshot(
          t,
          heap,
          "Heap máximo completo",
          "Se conserva un árbol completo y la propiedad de máximo.",
          "done",
          6,
          List.of(),
          Map.of(
              "tamaño",
              "" + heap.size(),
              "extraído",
              result,
              "máximo",
              heap.isEmpty() ? "vacío" : heap.getFirst().label()));
      return t.result(code);
    }

    private static void sink(CanvasTrace t, List<Node3D> heap, int i) {
      while (2 * i + 1 < heap.size()) {
        int l = 2 * i + 1, r = l + 1, best = l;
        if (r < heap.size() && CanvasTrace.value(heap.get(r)) > CanvasTrace.value(heap.get(l)))
          best = r;
        snapshot(
            t,
            heap,
            "Comparar con hijo mayor",
            "Se elige el mayor de los hijos existentes.",
            "visit",
            4,
            List.of(heap.get(i).id(), heap.get(best).id()),
            Map.of("padre", "" + i, "hijo", "" + best));
        if (CanvasTrace.value(heap.get(i)) >= CanvasTrace.value(heap.get(best))) break;
        Collections.swap(heap, i, best);
        snapshot(
            t,
            heap,
            "Hundir",
            "El mayor sube y el otro valor desciende.",
            "rotated",
            5,
            List.of(heap.get(best).id()),
            Map.of("índice", "" + best));
        i = best;
      }
    }

    private static void snapshot(
        CanvasTrace t,
        List<Node3D> heap,
        String title,
        String detail,
        String type,
        int line,
        List<String> ids,
        Map<String, String> vars) {
      var s = SequenceScene.place(heap, "heap", false, false);
      t.add(s.nodes(), s.edges(), title, detail, type, line, ids, vars);
    }
  }

  @Service
  public static class Heapify extends Operation {
    public Heapify() {
      super("heapify");
    }
  }

  @Service
  public static class Insert extends Operation {
    public Insert() {
      super("insert");
    }
  }

  @Service
  public static class Extract extends Operation {
    public Extract() {
      super("extract");
    }
  }

  @Service
  public static class Peek extends Operation {
    public Peek() {
      super("peek");
    }
  }
}
