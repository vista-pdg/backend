package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import com.vista.pdg.service.generator.impl.graph.TreeGenerator;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BstInsertAlgorithm implements AlgorithmStrategy {
  private final TreeGenerator generator;
  private final TreeLayout3D layout;

  public BstInsertAlgorithm(TreeGenerator generator, TreeLayout3D layout) {
    this.generator = generator;
    this.layout = layout;
  }

  public AlgorithmDescriptor descriptor() {
    return new AlgorithmDescriptor(
        "tree",
        "bst",
        "insert",
        "tree",
        "Inserción · Construir un BST",
        "Inserta valores; los duplicados no crean otro nodo",
        "values");
  }

  public StepsResponse generate(AlgorithmRequest r) {
    List<Integer> values = CanvasTrace.values(r, 64), inserted = new ArrayList<>();
    List<AlgorithmStep> steps = new ArrayList<>();
    List<String> code =
        List.of(
            "construir_bst(valores):",
            "  raíz ← nulo",
            "  para cada valor:",
            "    comparar y seguir la rama menor o mayor",
            "    si no existe: insertar una hoja",
            "  retornar raíz");
    steps.add(
        new AlgorithmStep(
            0,
            "BST vacío",
            "Se construye un BST nuevo con los valores dados.",
            "initial",
            List.of(),
            null,
            List.of(),
            List.of(),
            2,
            Map.of("insertados", "[]"),
            List.of()));
    for (int v : values) {
      GeneratedStructure before = build(inserted);
      Node3D cur = before.nodes().stream().filter(n -> n.parent() == null).findFirst().orElse(null);
      while (cur != null) {
        steps.add(
            new AlgorithmStep(
                steps.size(),
                "Comparar " + v + " con " + cur.label(),
                "El menor va a la izquierda; el mayor a la derecha.",
                "visit",
                List.of(cur.id()),
                null,
                before.nodes(),
                before.edges(),
                4,
                Map.of("valor", "" + v),
                List.of()));
        int cmp = Integer.compare(v, Integer.parseInt(cur.label()));
        if (cmp == 0) break;
        String parent = cur.id();
        cur =
            before.nodes().stream()
                .filter(
                    n ->
                        parent.equals(n.parent())
                            && (Integer.parseInt(n.label())
                                    < Integer.parseInt(
                                        before.nodes().stream()
                                            .filter(p -> p.id().equals(parent))
                                            .findFirst()
                                            .orElseThrow()
                                            .label()))
                                == (cmp < 0))
                .findFirst()
                .orElse(null);
      }
      boolean duplicate = inserted.contains(v);
      if (!duplicate) inserted.add(v);
      GeneratedStructure s = build(inserted);
      List<String> ids =
          s.nodes().stream().filter(n -> n.label().equals("" + v)).map(Node3D::id).toList();
      steps.add(
          new AlgorithmStep(
              steps.size(),
              duplicate ? "Duplicado omitido" : "Insertar " + v,
              duplicate ? "La clave ya existe." : "Se añade una hoja.",
              "insert",
              ids,
              null,
              s.nodes(),
              s.edges(),
              5,
              Map.of("insertados", inserted.toString()),
              List.of()));
    }
    GeneratedStructure s = build(inserted);
    steps.add(
        new AlgorithmStep(
            steps.size(),
            "BST completo",
            "Se conserva la propiedad de búsqueda.",
            "done",
            List.of(),
            null,
            s.nodes(),
            s.edges(),
            6,
            Map.of("insertados", inserted.toString()),
            List.of()));
    return StepsResponse.ok(List.copyOf(steps), code, "pseudocode");
  }

  private GeneratedStructure build(List<Integer> values) {
    var s =
        generator.generate(
            new TreeContract(
                "tree",
                null,
                "bst",
                List.of(new TreeContract.Operation("insert", List.copyOf(values))),
                null));
    Map<String, String> ids = new HashMap<>();
    s.nodes().forEach(n -> ids.put(n.id(), "bst:" + n.label()));
    s =
        new GeneratedStructure(
            null,
            s.nodes().stream()
                .map(
                    n ->
                        new Node3D(
                            ids.get(n.id()),
                            n.label(),
                            0,
                            0,
                            0,
                            n.depth(),
                            n.parent() == null ? null : ids.get(n.parent()),
                            Map.of()))
                .toList(),
            s.edges().stream()
                .map(
                    e ->
                        new Edge3D(
                            "link:" + ids.get(e.from()) + ":" + ids.get(e.to()),
                            ids.get(e.from()),
                            ids.get(e.to()),
                            null,
                            true))
                .toList(),
            Map.of());
    var pos = layout.compute(s);
    return new GeneratedStructure(
        null,
        s.nodes().stream()
            .map(
                n -> {
                  var p = pos.get(n.id());
                  return n.withPosition(p.x(), p.y(), p.z());
                })
            .toList(),
        s.edges(),
        s.computedProperties());
  }
}
