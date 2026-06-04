package com.vista.pdg.service.layout.impl;

import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LinearLayout3D implements LayoutStrategy {

  private static final double SPACING = 2.8;

  @Override
  public String supportedLayout() {
    return "linear3d";
  }

  @Override
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    List<Node3D> nodes = structure.nodes();
    Map<String, Vec3> positions = new HashMap<>();

    int n = nodes.size();
    double offset = -(n - 1) * SPACING / 2.0;

    for (int i = 0; i < n; i++) {
      positions.put(nodes.get(i).id(), Vec3.of(offset + i * SPACING, 0, 0));
    }

    return positions;
  }
}
