package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Each algorithm executes its own comparisons; the shared tracer only records immutable scenes. */
public final class SortingAlgorithms {
  private SortingAlgorithms() {}

  abstract static class Sort implements AlgorithmStrategy {
    private final AlgorithmDescriptor descriptor;
    final List<String> code;

    Sort(String operation, String label, String description, List<String> code) {
      this.code = code;
      descriptor =
          new AlgorithmDescriptor(
              "linked-list",
              "simple",
              operation,
              "sorting",
              label,
              description + "; hasta 32 enteros, menor a mayor",
              "values",
              null,
              32);
    }

    public AlgorithmDescriptor descriptor() {
      return descriptor;
    }

    public StepsResponse generate(AlgorithmRequest r) {
      Trace t = new Trace(CanvasTrace.values(r, 32));
      t.step(
          "Secuencia inicial",
          "Se ordena una secuencia nueva; los IDs acompañan a cada valor.",
          "initial",
          1,
          List.of());
      sort(t);
      t.step("Ordenamiento completo", "Resultado: " + t.labels(), "done", code.size(), List.of());
      return t.canvas.result(code);
    }

    abstract void sort(Trace t);
  }

  static final class Trace {
    final List<Node3D> items;
    final CanvasTrace canvas;
    int comparisons, swaps, writes;

    Trace(List<Integer> values) {
      items = SequenceScene.fromValues(values);
      var s = SequenceScene.place(items, "list", false, false);
      canvas = new CanvasTrace(s.nodes(), s.edges());
    }

    String labels() {
      return items.stream().map(Node3D::label).toList().toString();
    }

    void step(String title, String detail, String type, int line, List<Node3D> active) {
      var s = SequenceScene.place(items, "list", false, false);
      canvas.add(
          s.nodes(),
          s.edges(),
          title,
          detail,
          type,
          line,
          active.stream().map(Node3D::id).toList(),
          Map.of(
              "secuencia",
              labels(),
              "comparaciones",
              "" + comparisons,
              "intercambios",
              "" + swaps,
              "escrituras",
              "" + writes));
    }

    int compare(Node3D a, Node3D b, int line) {
      comparisons++;
      step(
          "Comparar " + a.label() + " y " + b.label(),
          "Se comparan sus valores, sin modificar la secuencia.",
          "visit",
          line,
          List.of(a, b));
      return Integer.compare(CanvasTrace.value(a), CanvasTrace.value(b));
    }

    void swap(int a, int b, int line) {
      if (a == b) return;
      Collections.swap(items, a, b);
      swaps++;
      writes += 2;
      step(
          "Intercambiar posiciones " + a + " y " + b,
          "Los elementos conservan sus IDs al cambiar de posición.",
          "rotated",
          line,
          List.of(items.get(a), items.get(b)));
    }
  }

  @Service
  public static class Bubble extends Sort {
    public Bubble() {
      super(
          "bubble-sort",
          "Burbuja · Comparar vecinos",
          "Estable; O(n²), se detiene si no hay cambios",
          List.of(
              "burbuja(A):",
              "  para fin desde n-1 hasta 1:",
              "    cambios ← falso",
              "    comparar cada pareja vecina hasta fin",
              "    si están invertidos: intercambiar; cambios ← verdadero",
              "    si no hubo cambios: terminar",
              "  retornar A"));
    }

    void sort(Trace t) {
      for (int end = t.items.size() - 1; end > 0; end--) {
        boolean changed = false;
        for (int j = 0; j < end; j++)
          if (t.compare(t.items.get(j), t.items.get(j + 1), 4) > 0) {
            t.swap(j, j + 1, 5);
            changed = true;
          }
        if (!changed) break;
      }
    }
  }

  @Service
  public static class Selection extends Sort {
    public Selection() {
      super(
          "selection-sort",
          "Selección · Elegir el mínimo",
          "No estable; O(n²), un intercambio por posición",
          List.of(
              "selección(A):",
              "  para i desde 0 hasta n-2:",
              "    mínimo ← i",
              "    comparar cada posición restante con mínimo",
              "    actualizar mínimo si se encuentra uno menor",
              "    intercambiar A[i] con A[mínimo]",
              "  retornar A"));
    }

    void sort(Trace t) {
      for (int i = 0; i < t.items.size() - 1; i++) {
        int min = i;
        for (int j = i + 1; j < t.items.size(); j++)
          if (t.compare(t.items.get(j), t.items.get(min), 4) < 0) min = j;
        t.swap(i, min, 6);
      }
    }
  }

  @Service
  public static class Insertion extends Sort {
    public Insertion() {
      super(
          "insertion-sort",
          "Inserción · Prefijo ordenado",
          "Estable; O(n²), mueve cada valor al prefijo",
          List.of(
              "inserción(A):",
              "  para i desde 1 hasta n-1:",
              "    j ← i",
              "    comparar A[j-1] con A[j] mientras j > 0",
              "    si están invertidos: intercambiar; j ← j-1",
              "    si no: terminar esta inserción",
              "  retornar A"));
    }

    void sort(Trace t) {
      for (int i = 1; i < t.items.size(); i++)
        for (int j = i; j > 0; j--) {
          if (t.compare(t.items.get(j - 1), t.items.get(j), 4) <= 0) break;
          t.swap(j - 1, j, 5);
        }
    }
  }

  @Service
  public static class Merge extends Sort {
    public Merge() {
      super(
          "merge-sort",
          "Merge sort · Dividir y mezclar",
          "Estable; O(n log n), buffer auxiliar O(n)",
          List.of(
              "merge_sort(A, inicio, fin):",
              "  si el rango tiene menos de dos elementos: retornar",
              "  dividir y ordenar ambas mitades",
              "  comparar frentes de las mitades ordenadas",
              "  añadir el menor al buffer; copiar sobrantes",
              "  copiar el buffer al rango original",
              "  retornar A"));
    }

    void sort(Trace t) {
      merge(t, 0, t.items.size());
    }

    private void merge(Trace t, int lo, int hi) {
      t.canvas.frames.addLast(
          new AlgorithmStep.Frame("merge_sort", Map.of("inicio", "" + lo, "fin", "" + hi)));
      t.step(
          "merge_sort [" + lo + ", " + hi + ")",
          "Llamada sobre este rango.",
          "frontier",
          1,
          List.of());
      if (hi - lo < 2) {
        t.step("Caso base", "El rango ya está ordenado.", "done", 2, List.of());
        t.canvas.frames.removeLast();
        return;
      }
      int mid = (lo + hi) / 2;
      merge(t, lo, mid);
      merge(t, mid, hi);
      List<Node3D> left = new ArrayList<>(t.items.subList(lo, mid)),
          right = new ArrayList<>(t.items.subList(mid, hi)),
          buffer = new ArrayList<>();
      int i = 0, j = 0;
      while (i < left.size() && j < right.size()) {
        if (t.compare(left.get(i), right.get(j), 4) <= 0) buffer.add(left.get(i++));
        else buffer.add(right.get(j++));
        t.writes++;
      }
      while (i < left.size()) {
        buffer.add(left.get(i++));
        t.writes++;
      }
      while (j < right.size()) {
        buffer.add(right.get(j++));
        t.writes++;
      }
      for (int k = 0; k < buffer.size(); k++) {
        t.items.set(lo + k, buffer.get(k));
        t.writes++;
      }
      t.step(
          "Mezclar rango [" + lo + ", " + hi + ")",
          "Las dos mitades ordenadas se combinan con estabilidad.",
          "insert",
          6,
          buffer);
      t.canvas.frames.removeLast();
    }
  }

  @Service
  public static class Quick extends Sort {
    public Quick() {
      super(
          "quick-sort",
          "Quicksort · Partición por pivote",
          "No estable; O(n log n) promedio, O(n²) peor caso",
          List.of(
              "quicksort(A, inicio, fin):",
              "  si inicio >= fin: retornar",
              "  pivote ← A[fin]; frontera ← inicio",
              "  comparar cada valor con pivote",
              "  si valor <= pivote: intercambiar con frontera; avanzar",
              "  colocar pivote en frontera",
              "  ordenar recursivamente los dos lados",
              "  retornar A"));
    }

    void sort(Trace t) {
      quick(t, 0, t.items.size() - 1);
    }

    private void quick(Trace t, int lo, int hi) {
      t.canvas.frames.addLast(
          new AlgorithmStep.Frame("quicksort", Map.of("inicio", "" + lo, "fin", "" + hi)));
      t.step(
          "quicksort [" + lo + ", " + hi + "]",
          "Llamada sobre este rango.",
          "frontier",
          1,
          List.of());
      if (lo >= hi) {
        t.step("Caso base", "El rango ya está ordenado.", "done", 2, List.of());
        t.canvas.frames.removeLast();
        return;
      }
      Node3D pivot = t.items.get(hi);
      int i = lo;
      for (int j = lo; j < hi; j++) if (t.compare(t.items.get(j), pivot, 4) <= 0) t.swap(i++, j, 5);
      t.swap(i, hi, 6);
      t.step(
          "Pivote en posición " + i,
          "El pivote queda en su posición final para este rango.",
          "frontier",
          6,
          List.of(pivot));
      quick(t, lo, i - 1);
      quick(t, i + 1, hi);
      t.canvas.frames.removeLast();
    }
  }

  @Service
  public static class Heap extends Sort {
    public Heap() {
      super(
          "heap-sort",
          "Heapsort · Heap máximo",
          "No estable; O(n log n), heap sobre la secuencia",
          List.of(
              "heapsort(A):",
              "  construir heap máximo de abajo hacia arriba",
              "  comparar padre con el mayor hijo del heap",
              "  si es menor: intercambiar y continuar hundiendo",
              "  intercambiar raíz con último del heap; reducir heap",
              "  repetir el hundimiento hasta ordenar",
              "  retornar A"));
    }

    void sort(Trace t) {
      int n = t.items.size();
      for (int i = n / 2 - 1; i >= 0; i--) sink(t, n, i);
      for (int end = n - 1; end > 0; end--) {
        t.swap(0, end, 5);
        sink(t, end, 0);
      }
    }

    private void sink(Trace t, int n, int i) {
      while (2 * i + 1 < n) {
        int l = 2 * i + 1, r = l + 1, best = l;
        if (r < n && t.compare(t.items.get(r), t.items.get(l), 3) > 0) best = r;
        if (t.compare(t.items.get(i), t.items.get(best), 3) >= 0) return;
        t.swap(i, best, 4);
        i = best;
      }
    }
  }
}
