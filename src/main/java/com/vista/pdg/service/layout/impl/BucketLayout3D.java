package com.vista.pdg.service.layout.impl;

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
public class BucketLayout3D implements LayoutStrategy {

  private static final double H_SPACING = 3.5;
  private static final double V_SPACING = 2.5;

  @Override
  public String supportedLayout() {
    return "bucket3d";
  }

  @Override
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    List<Node3D> nodes = structure.nodes();
    Map<String, Vec3> positions = new HashMap<>();

    List<Node3D> buckets = nodes.stream().filter(n -> n.parent() == null).toList();
    int bucketCount = buckets.size();
    double hOffset = -(bucketCount - 1) * H_SPACING / 2.0;

    Map<String, Double> bucketX = new HashMap<>();
    for (int i = 0; i < buckets.size(); i++) {
      double x = hOffset + i * H_SPACING;
      bucketX.put(buckets.get(i).id(), x);
      positions.put(buckets.get(i).id(), Vec3.of(x, 0, 0));
    }

    Map<String, Node3D> nodeById =
        nodes.stream().collect(Collectors.toMap(Node3D::id, n -> n));

    for (Node3D node : nodes) {
      if (node.parent() == null) continue;
      double x = findBucketX(node, nodeById, bucketX);
      positions.put(node.id(), Vec3.of(x, -node.depth() * V_SPACING, 0));
    }

    return positions;
  }

  private double findBucketX(
      Node3D node, Map<String, Node3D> nodeById, Map<String, Double> bucketX) {
    String current = node.parent();
    while (current != null) {
      if (bucketX.containsKey(current)) return bucketX.get(current);
      Node3D parent = nodeById.get(current);
      if (parent == null) break;
      current = parent.parent();
    }
    return 0.0;
  }
}
