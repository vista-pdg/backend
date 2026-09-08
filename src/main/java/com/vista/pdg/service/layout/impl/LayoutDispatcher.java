package com.vista.pdg.service.layout.impl;

import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LayoutDispatcher {

  private final Map<String, LayoutStrategy> strategies;

  public LayoutDispatcher(List<LayoutStrategy> all) {
    this.strategies =
        all.stream().collect(Collectors.toMap(LayoutStrategy::supportedLayout, s -> s));
  }

  /**
   * Calcula posiciones y <b>garantiza una por nodo</b>: un nodo que la estrategia no colocó (un
   * bosque, una arista rota) no cae al origen encima de los demás, sino en una rejilla aparte.
   */
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    String key = resolveLayout(structure);
    LayoutStrategy strategy = strategies.get(key);
    if (strategy == null)
      strategy = strategies.getOrDefault(defaultFor(structure), strategies.get("force3d"));
    Map<String, Vec3> positions = new HashMap<>(strategy.compute(structure));
    return fillMissing(structure, positions);
  }

  public static Map<String, Vec3> fillMissing(
      GeneratedStructure structure, Map<String, Vec3> positions) {
    List<Node3D> missing =
        structure.nodes().stream().filter(n -> !positions.containsKey(n.id())).toList();
    if (missing.isEmpty()) return positions;
    double maxX = positions.values().stream().mapToDouble(Vec3::x).max().orElse(0);
    double maxY = positions.values().stream().mapToDouble(Vec3::y).max().orElse(0);
    int cols = (int) Math.ceil(Math.sqrt(missing.size()));
    for (int i = 0; i < missing.size(); i++) {
      positions.put(
          missing.get(i).id(),
          Vec3.of(maxX + 4 + (i % cols) * 2.5, maxY + 3 + (i / cols) * 2.5, 0));
    }
    return positions;
  }

  private String resolveLayout(GeneratedStructure structure) {
    // La pista del modelo sólo vale si nombra una estrategia real; si no, manda el tipo.
    if (structure.contract().visual() != null && structure.contract().visual().layout() != null) {
      String hinted = structure.contract().visual().layout();
      if (strategies.containsKey(hinted)) return hinted;
    }
    return defaultFor(structure);
  }

  private String defaultFor(GeneratedStructure structure) {
    return switch (structure.contract().type()) {
      case "tree" -> "hierarchical3d";
      case "linked-list" -> resolveLinkedListLayout(structure);
      case "hash-table" -> "bucket3d";
        // HU-19: la pila crece hacia arriba; la cola es una fila del frente al final.
      case "stack" -> "stack3d";
      case "queue" -> "linear3d";
      default -> "force3d";
    };
  }

  private String resolveLinkedListLayout(GeneratedStructure structure) {
    if (structure.contract() instanceof LinkedListContract l && "circular".equals(l.subtype())) {
      return "circular3d";
    }
    return "linear3d";
  }
}
