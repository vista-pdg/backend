package com.vista.pdg.service.layout.impl.graph;

import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CircularLayout3D implements LayoutStrategy {

  private static final double RADIUS = 4.0;

  @Override
  public String supportedLayout() {
    return "circular3d";
  }

  @Override
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    Map<String, Vec3> positions = new HashMap<>();
    List<Node3D> nodes = structure.nodes();
    if (nodes.isEmpty()) return positions;
    for (int i = 0; i < nodes.size(); i++) {
      double angle = 2 * Math.PI * i / nodes.size();
      positions.put(
          nodes.get(i).id(), Vec3.of(RADIUS * Math.cos(angle), 0, RADIUS * Math.sin(angle)));
    }
    return positions;
  }
}
