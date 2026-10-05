package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.exception.InvalidAlgorithmInputException;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import com.vista.pdg.service.layout.impl.BucketLayout3D;
import java.util.*;
import org.springframework.stereotype.Service;

public final class HashBasicsAlgorithms {
  private HashBasicsAlgorithms() {}

  abstract static class Operation implements AlgorithmStrategy {
    private final String operation;

    Operation(String operation) {
      this.operation = operation;
    }

    public AlgorithmDescriptor descriptor() {
      return new AlgorithmDescriptor(
          "hash-table",
          "chaining",
          operation,
          "hash-table",
          switch (operation) {
            case "search" -> "Búsqueda · Consultar una clave";
            case "insert" -> "Inserción · Encadenamiento separado";
            default -> "Eliminar primera coincidencia";
          },
          "Hash |valor % buckets|; colisiones por encadenamiento",
          "structure",
          operation.equals("insert") ? "value" : "target");
    }

    public StepsResponse generate(AlgorithmRequest r) {
      int value = CanvasTrace.argument(r);
      CanvasTrace t = new CanvasTrace(r);
      var buckets =
          t.nodes.stream()
              .filter(
                  n -> n.properties() != null && Boolean.TRUE.equals(n.properties().get("bucket")))
              .sorted(Comparator.comparingInt(Operation::bucketNumber))
              .toList();
      if (buckets.isEmpty())
        throw new InvalidAlgorithmInputException(
            "Se requiere una tabla hash con buckets del lienzo.");
      for (int i = 0; i < buckets.size(); i++)
        if (bucketNumber(buckets.get(i)) != i)
          throw new InvalidAlgorithmInputException(
              "Los buckets deben estar numerados desde cero sin huecos.");
      Map<String, String> next = new HashMap<>();
      Set<String> childIds = new HashSet<>();
      for (Edge3D e : t.edges) {
        if (!e.directed() || next.put(e.from(), e.to()) != null || !childIds.add(e.to()))
          throw new InvalidAlgorithmInputException(
              "Las colisiones deben formar cadenas sin bifurcaciones.");
      }
      List<List<Node3D>> chains = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < buckets.size(); i++) {
        Node3D bucket = buckets.get(i);
        seen.add(bucket.id());
        List<Node3D> chain = new ArrayList<>();
        String id = next.get(bucket.id()), prev = bucket.id();
        while (id != null) {
          if (!seen.add(id))
            throw new InvalidAlgorithmInputException(
                "La tabla hash contiene un ciclo o un bucket enlazado.");
          Node3D n = t.byId.get(id);
          int key = CanvasTrace.value(n);
          Object b = n.properties() == null ? null : n.properties().get("hashBucket");
          if (!(b instanceof Number)
              || ((Number) b).doubleValue() != i
              || Math.abs(key % buckets.size()) != i
              || (n.parent() != null && !n.parent().equals(prev)))
            throw new InvalidAlgorithmInputException(
                "Una clave no coincide con su bucket o cadena.");
          chain.add(n);
          prev = id;
          id = next.get(id);
        }
        chains.add(chain);
      }
      if (seen.size() != t.nodes.size())
        throw new InvalidAlgorithmInputException("La tabla hash contiene claves desconectadas.");
      int index = Math.abs(value % buckets.size());
      Node3D bucket = buckets.get(index);
      var chain = chains.get(index);
      List<String> code =
          List.of(
              operation + "(tabla, valor):",
              "  bucket ← |valor % cantidad_de_buckets|",
              "  recorrer la cadena del bucket",
              "  comparar cada clave y detenerse en la primera coincidencia",
              "  insertar al final / eliminar la coincidencia / consultar",
              "  retornar resultado");
      t.add(
          "Bucket " + index,
          "Hash: |" + value + " % " + buckets.size() + "| = " + index,
          "frontier",
          2,
          List.of(bucket.id()),
          Map.of("bucket", "" + index, "valor", "" + value));
      Node3D found = null;
      for (Node3D n : chain) {
        t.add(
            "Comparar con " + n.label(),
            "Se recorre la cadena de colisiones.",
            "visit",
            4,
            List.of(n.id()),
            Map.of("bucket", "" + index, "valor", "" + value));
        if (CanvasTrace.value(n) == value) {
          found = n;
          if (!operation.equals("insert")) break;
        }
      }
      String message;
      if (operation.equals("insert")) {
        if (t.nodes.size() >= 64)
          throw new InvalidAlgorithmInputException("Usa hasta 64 nodos para esta demostración.");
        Node3D added =
            new Node3D(
                SequenceScene.freshId(t.nodes),
                "" + value,
                0,
                0,
                0,
                0,
                null,
                Map.of("hashBucket", index));
        chain.add(added);
        var s = scene(buckets, chains);
        t.add(
            s.nodes(),
            s.edges(),
            "Insertar " + value,
            "Se añade al final de la cadena; se permiten duplicados.",
            "insert",
            5,
            List.of(added.id()),
            Map.of("bucket", "" + index, "valor", "" + value));
        message = "Clave insertada.";
      } else if (operation.equals("delete")) {
        if (found != null) chain.remove(found);
        message =
            found == null ? "Clave ausente." : "Primera coincidencia eliminada; cadena reenlazada.";
      } else
        message = found == null ? "Clave ausente." : "Clave encontrada sin modificar la tabla.";
      Map<String, String> vars = new LinkedHashMap<>();
      vars.put("bucket", "" + index);
      vars.put("encontrado", "" + (found != null));
      vars.put("claves", "" + chains.stream().mapToInt(List::size).sum());
      if (operation.equals("search") || (operation.equals("delete") && found == null))
        t.add(
            "Resultado", message, "done", 6, found == null ? List.of() : List.of(found.id()), vars);
      else {
        var s = scene(buckets, chains);
        t.add(s.nodes(), s.edges(), "Resultado", message, "done", 6, List.of(), vars);
      }
      return t.result(code);
    }

    private static int bucketNumber(Node3D n) {
      try {
        if (!n.label().matches("\\[\\d+\\]")) throw new NumberFormatException();
        return Integer.parseInt(n.label().substring(1, n.label().length() - 1));
      } catch (NumberFormatException e) {
        throw new InvalidAlgorithmInputException("Un bucket debe tener etiqueta [índice].");
      }
    }

    private static SequenceScene.Scene scene(List<Node3D> buckets, List<List<Node3D>> chains) {
      List<Node3D> nodes = new ArrayList<>(buckets);
      List<Edge3D> edges = new ArrayList<>();
      for (int i = 0; i < buckets.size(); i++) {
        String prev = buckets.get(i).id();
        int depth = 1;
        for (Node3D n : chains.get(i)) {
          Map<String, Object> props = new LinkedHashMap<>();
          if (n.properties() != null) props.putAll(n.properties());
          props.put("hashBucket", i);
          nodes.add(
              new Node3D(n.id(), n.label(), n.x(), n.y(), n.z(), depth++, prev, Map.copyOf(props)));
          edges.add(new Edge3D("link:" + prev + ":" + n.id(), prev, n.id(), null, true));
          prev = n.id();
        }
      }
      var pos = new BucketLayout3D().compute(new GeneratedStructure(null, nodes, edges, Map.of()));
      return new SequenceScene.Scene(
          nodes.stream()
              .map(
                  n -> {
                    var p = pos.get(n.id());
                    return n.withPosition(p.x(), p.y(), p.z());
                  })
              .toList(),
          List.copyOf(edges));
    }
  }

  @Service
  public static class Search extends Operation {
    public Search() {
      super("search");
    }
  }

  @Service
  public static class Insert extends Operation {
    public Insert() {
      super("insert");
    }
  }

  @Service
  public static class Delete extends Operation {
    public Delete() {
      super("delete");
    }
  }
}
