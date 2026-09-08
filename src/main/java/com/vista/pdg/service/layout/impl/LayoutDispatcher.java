package com.vista.pdg.service.layout.impl;

import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
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

  public Map<String, Vec3> compute(GeneratedStructure structure) {
    String key = resolveLayout(structure);
    LayoutStrategy strategy = strategies.getOrDefault(key, strategies.get("force3d"));
    return strategy.compute(structure);
  }

  private String resolveLayout(GeneratedStructure structure) {
    if (structure.contract().visual() != null && structure.contract().visual().layout() != null) {
      return structure.contract().visual().layout();
    }
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
