package com.vista.pdg.service.layout.impl;

import com.vista.pdg.model.contract.RelationContract;
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
      case "lattice" -> "levels3d";
      case "relation" -> resolveRelationLayout(structure);
      default -> "force3d";
    };
  }

  private String resolveRelationLayout(GeneratedStructure structure) {
    if (structure.contract() instanceof RelationContract r && r.check() != null) {
      boolean reflexive = isTrue(structure, "reflexive");
      boolean symmetric = isTrue(structure, "symmetric");
      boolean transitive = isTrue(structure, "transitive");
      boolean antisymmetric = isTrue(structure, "antisymmetric");
      if (reflexive && symmetric && transitive) return "cluster3d";
      if (antisymmetric && transitive) return "levels3d";
    }
    return "circular3d";
  }

  private boolean isTrue(GeneratedStructure structure, String key) {
    return Boolean.TRUE.equals(structure.computedProperties().get(key));
  }
}
