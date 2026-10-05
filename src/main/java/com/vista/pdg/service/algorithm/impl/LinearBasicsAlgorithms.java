package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import java.util.*;
import org.springframework.stereotype.Service;

public final class LinearBasicsAlgorithms {
  private LinearBasicsAlgorithms() {}

  abstract static class Build implements AlgorithmStrategy {
    private final String kind, operation;

    Build(String kind, String operation) {
      this.kind = kind;
      this.operation = operation;
    }

    public AlgorithmDescriptor descriptor() {
      return new AlgorithmDescriptor(
          kind,
          "simple",
          operation,
          kind,
          operation.equals("push") ? "Push · Apilar valores" : "Enqueue · Encolar valores",
          operation.equals("push")
              ? "Construye una pila añadiendo cada valor al tope"
              : "Construye una cola añadiendo cada valor al final",
          "values");
    }

    public StepsResponse generate(AlgorithmRequest r) {
      var all = SequenceScene.fromValues(CanvasTrace.values(r, 64));
      CanvasTrace t = new CanvasTrace(all, List.of());
      List<Node3D> prefix = new ArrayList<>();
      var code =
          List.of(
              "construir(valores):",
              "  estructura ← vacía",
              "  para cada valor:",
              "    añadir al " + (kind.equals("stack") ? "tope" : "final"),
              "  retornar estructura");
      t.add(
          List.of(),
          List.of(),
          "Estructura vacía",
          "Se construye una demostración nueva.",
          "initial",
          2,
          List.of(),
          Map.of("tamaño", "0"));
      for (Node3D n : all) {
        prefix.add(n);
        var s = SequenceScene.place(prefix, kind, false, false);
        t.add(
            s.nodes(),
            s.edges(),
            operation + "(" + n.label() + ")",
            "El elemento se añade al " + (kind.equals("stack") ? "tope" : "final") + ".",
            "insert",
            4,
            List.of(n.id()),
            Map.of("valor", n.label(), "tamaño", "" + prefix.size()));
      }
      var s = SequenceScene.place(prefix, kind, false, false);
      t.add(
          s.nodes(),
          s.edges(),
          "Construcción completa",
          kind.equals("stack")
              ? "LIFO: el último en entrar sale primero."
              : "FIFO: el primero en entrar sale primero.",
          "done",
          5,
          List.of(),
          Map.of(
              "tamaño",
              "" + prefix.size(),
              kind.equals("stack") ? "tope" : "frente",
              (kind.equals("stack") ? prefix.getLast() : prefix.getFirst()).label()));
      return t.result(code);
    }
  }

  abstract static class Peek implements AlgorithmStrategy {
    private final String kind;

    Peek(String kind) {
      this.kind = kind;
    }

    public AlgorithmDescriptor descriptor() {
      return new AlgorithmDescriptor(
          kind,
          "simple",
          "peek",
          kind,
          kind.equals("stack") ? "Peek · Consultar el tope" : "Front · Consultar el frente",
          "Consulta un elemento sin retirarlo del lienzo",
          "structure");
    }

    public StepsResponse generate(AlgorithmRequest r) {
      CanvasTrace t = new CanvasTrace(r);
      var items = SequenceScene.ordered(t);
      if (t.nodes.stream()
              .anyMatch(n -> n.properties() == null || n.properties().get("role") == null)
          || (!t.nodes.stream()
              .anyMatch(
                  n ->
                      kind.equals("stack")
                          ? "top".equals(n.properties().get("role"))
                          : "front".equals(n.properties().get("role")))))
        throw new InvalidAlgorithmInputException(
            "Genera una " + (kind.equals("stack") ? "pila" : "cola") + " para esta consulta.");
      Node3D n = kind.equals("stack") ? items.getLast() : items.getFirst();
      t.add(
          "Estado inicial",
          "La consulta no modifica la estructura.",
          "initial",
          1,
          List.of(),
          Map.of("tamaño", "" + items.size()));
      t.add(
          "Consulta: " + n.label(),
          "El elemento permanece en el lienzo.",
          "done",
          2,
          List.of(n.id()),
          Map.of("resultado", n.label(), "tamaño", "" + items.size()));
      return t.result(
          List.of(
              "consultar(estructura):",
              "  retornar " + (kind.equals("stack") ? "tope" : "frente") + " sin retirarlo"));
    }
  }

  abstract static class ListOperation implements AlgorithmStrategy {
    private final AlgorithmDescriptor descriptor;
    private final String operation;

    ListOperation(String op, String label, String parameter) {
      operation = op;
      descriptor =
          new AlgorithmDescriptor(
              "linked-list",
              "simple",
              op,
              "linked-list",
              label,
              "Sobre la lista del lienzo; conserva los IDs de los elementos",
              "structure",
              parameter);
    }

    public AlgorithmDescriptor descriptor() {
      return descriptor;
    }

    public StepsResponse generate(AlgorithmRequest r) {
      CanvasTrace t = new CanvasTrace(r);
      List<Node3D> items = SequenceScene.ordered(t);
      if (t.nodes.stream()
          .anyMatch(
              n ->
                  n.properties() != null
                      && (n.properties().containsKey("role")
                          || Boolean.TRUE.equals(n.properties().get("bucket")))))
        throw new InvalidAlgorithmInputException("Se requiere una lista enlazada.");
      Integer target = descriptor.parameter() == null ? null : CanvasTrace.argument(r);
      // Two-node circular and doubly linked lists have identical edges; keep explicit metadata.
      String subtype =
          t.nodes.getFirst().properties() == null
              ? null
              : Objects.toString(t.nodes.getFirst().properties().get("listSubtype"), null);
      boolean doubly =
          "doubly".equals(subtype)
              || (subtype == null
                  && t.edges.stream()
                      .anyMatch(
                          e ->
                              t.edges.stream()
                                  .anyMatch(
                                      back ->
                                          back.from().equals(e.to())
                                              && back.to().equals(e.from()))));
      boolean circular =
          "circular".equals(subtype)
              || (subtype == null
                  && !doubly
                  && t.edges.stream()
                      .anyMatch(
                          e ->
                              e.from().equals(items.getLast().id())
                                  && e.to().equals(items.getFirst().id())));
      SequenceScene.validateListLinks(t, items, doubly, circular);
      List<String> code =
          switch (operation) {
            case "traverse" ->
                List.of(
                    "recorrer(cabeza):",
                    "  seguir enlaces sin visitar dos veces el mismo nodo",
                    "  añadir cada valor a salida",
                    "  retornar salida");
            case "search" ->
                List.of(
                    "buscar(cabeza, objetivo):",
                    "  recorrer hasta fin o vuelta a la cabeza",
                    "  comparar el valor actual",
                    "  retornar primera coincidencia o no_encontrado");
            case "append" ->
                List.of(
                    "añadir_final(lista, valor):",
                    "  recorrer hasta la cola",
                    "  enlazar el nuevo nodo y actualizar cola/ciclo",
                    "  retornar lista");
            case "delete" ->
                List.of(
                    "eliminar_primero(lista, objetivo):",
                    "  recorrer conservando el anterior",
                    "  comparar el valor actual",
                    "  reenlazar sin la primera coincidencia; retornar lista");
            default ->
                List.of(
                    "invertir(lista):",
                    "  recorrer conservando anterior, actual y siguiente",
                    "  invertir el enlace del nodo actual",
                    "  intercambiar cabeza y cola; retornar lista");
          };
      t.add(
          "Lista inicial",
          "La cabeza determina el inicio del recorrido.",
          "initial",
          1,
          List.of(),
          Map.of("tamaño", "" + items.size()));
      if (operation.equals("reverse")) {
        List<String> reverseCode =
            List.of(
                "invertir_enlaces(lista):",
                "  obtener el orden de los nodos en una secuencia auxiliar",
                "  izquierda ← 0; derecha ← tamaño - 1",
                "  mientras izquierda < derecha: intercambiar nodos y reenlazar",
                "  avanzar izquierda y derecha hacia el centro",
                "  retornar lista con cabeza y cola intercambiadas");
        for (int left = 0, right = items.size() - 1; left < right; left++, right--) {
          Collections.swap(items, left, right);
          var s = SequenceScene.place(items, "list", doubly, circular);
          t.add(
              s.nodes(),
              s.edges(),
              "Intercambiar posiciones " + left + " y " + right,
              "Se invierte el orden con índices auxiliares y se actualizan los enlaces.",
              "rotated",
              4,
              List.of(items.get(left).id(), items.get(right).id()),
              Map.of("izquierda", "" + left, "derecha", "" + right));
        }
        var s = SequenceScene.place(items, "list", doubly, circular);
        t.add(
            s.nodes(),
            s.edges(),
            "Lista invertida",
            "Los IDs y el tipo de enlaces se conservan.",
            "done",
            6,
            List.of(),
            Map.of("tamaño", "" + items.size()));
        return t.result(reverseCode);
      }
      List<String> output = new ArrayList<>();
      Node3D found = null;
      for (int i = 0; i < items.size(); i++) {
        Node3D n = items.get(i);
        output.add(n.label());
        boolean match = target != null && CanvasTrace.value(n) == target;
        t.add(
            "Visitar " + n.label(),
            "Posición " + i,
            "visit",
            operation.equals("append") ? 2 : 3,
            List.of(n.id()),
            Map.of("índice", "" + i, "salida", output.toString()));
        if ((operation.equals("search") || operation.equals("delete")) && match) {
          found = n;
          break;
        }
      }
      String detail;
      Map<String, String> vars = new LinkedHashMap<>();
      vars.put("tamaño", "" + items.size());
      switch (operation) {
        case "append" -> {
          if (items.size() >= 64)
            throw new InvalidAlgorithmInputException(
                "La lista admite hasta 64 nodos en la demostración.");
          Node3D added =
              new Node3D(SequenceScene.freshId(items), "" + target, 0, 0, 0, 0, null, Map.of());
          items.add(added);
          var s = SequenceScene.place(items, "list", doubly, circular);
          t.add(
              s.nodes(),
              s.edges(),
              "Añadir " + target,
              "Se actualizan los enlaces y la cola.",
              "insert",
              3,
              List.of(added.id()),
              Map.of("valor", "" + target, "tamaño", "" + items.size()));
          detail = "Elemento añadido al final.";
        }
        case "delete" -> {
          if (found != null) items.remove(found);
          detail =
              found == null
                  ? "El valor no está en la lista."
                  : "Se elimina solo la primera coincidencia.";
          vars.put("encontrado", "" + (found != null));
        }
        case "search" -> {
          detail = found == null ? "Valor ausente." : "Primera coincidencia: " + found.label();
          vars.put("encontrado", "" + (found != null));
        }
        default -> {
          detail = "Recorrido completo.";
          vars.put("salida", output.toString());
        }
      }
      vars.put("tamaño", "" + items.size());
      if (operation.equals("search")
          || operation.equals("traverse")
          || (operation.equals("delete") && found == null))
        t.add(
            "Resultado", detail, "done", 4, found == null ? List.of() : List.of(found.id()), vars);
      else {
        var s = SequenceScene.place(items, "list", doubly, circular);
        t.add(s.nodes(), s.edges(), "Resultado", detail, "done", 4, List.of(), vars);
      }
      return t.result(code);
    }
  }

  @Service
  public static class Push extends Build {
    public Push() {
      super("stack", "push");
    }
  }

  @Service
  public static class Enqueue extends Build {
    public Enqueue() {
      super("queue", "enqueue");
    }
  }

  @Service
  public static class StackPeek extends Peek {
    public StackPeek() {
      super("stack");
    }
  }

  @Service
  public static class QueuePeek extends Peek {
    public QueuePeek() {
      super("queue");
    }
  }

  @Service
  public static class TraverseList extends ListOperation {
    public TraverseList() {
      super("traverse", "Recorrido · Seguir enlaces", null);
    }
  }

  @Service
  public static class SearchList extends ListOperation {
    public SearchList() {
      super("search", "Búsqueda · Primera coincidencia", "target");
    }
  }

  @Service
  public static class AppendList extends ListOperation {
    public AppendList() {
      super("append", "Añadir al final", "value");
    }
  }

  @Service
  public static class DeleteList extends ListOperation {
    public DeleteList() {
      super("delete", "Eliminar primera coincidencia", "target");
    }
  }

  @Service
  public static class ReverseList extends ListOperation {
    public ReverseList() {
      super("reverse", "Invertir enlaces", null);
    }
  }
}
