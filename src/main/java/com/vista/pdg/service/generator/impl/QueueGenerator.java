package com.vista.pdg.service.generator.impl;

import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Cola (HU-19). {@code n0} es el frente y el último el final; aristas dirigidas del frente hacia el
 * final, que es el orden de salida. {@code properties.role} vale {@code front} / {@code rear} /
 * {@code middle} (un único elemento es a la vez frente y final: {@code front}).
 */
@Service
public class QueueGenerator implements StructureGenerator<QueueContract> {

  @Override
  public String supportedType() {
    return "queue";
  }

  @Override
  public GeneratedStructure generate(QueueContract contract) {
    List<Integer> values = contract.values();
    return new GeneratedStructure(
        contract,
        queueNodes(values),
        queueEdges(values.size()),
        Map.of("size", values.size(), "front", values.getFirst(), "rear", values.getLast()));
  }

  public static List<Node3D> queueNodes(List<Integer> values) {
    int n = values.size();
    List<Node3D> nodes = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      nodes.add(
          new Node3D(
              "n" + i,
              String.valueOf(values.get(i)),
              0,
              0,
              0,
              0,
              null,
              Map.of("index", i, "role", roleOf(i, n))));
    }
    return nodes;
  }

  public static List<Edge3D> queueEdges(int n) {
    List<Edge3D> edges = new ArrayList<>();
    for (int i = 0; i < n - 1; i++) {
      edges.add(new Edge3D("e" + i, "n" + i, "n" + (i + 1), null, true));
    }
    return edges;
  }

  private static String roleOf(int i, int n) {
    if (i == 0) return "front";
    if (i == n - 1) return "rear";
    return "middle";
  }
}
