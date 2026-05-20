package com.vista.pdg.service.layout.impl.graph;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TreeLayout3D implements LayoutStrategy {

  private static final double Y_STEP = 2.0;
  private static final double BASE_RADIUS = 3.0;

  @Override
  public String supportedLayout() {
    return "hierarchical3d";
  }

  @Override
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    Map<String, Vec3> positions = new HashMap<>();
    if (structure.nodes().isEmpty()) return positions;

    Map<String, List<String>> children = new HashMap<>();
    String root = null;
    for (Node3D node : structure.nodes()) {
      children.put(node.id(), new ArrayList<>());
      if (node.parent() == null && root == null) root = node.id();
    }
    for (Edge3D edge : structure.edges()) {
      children.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
    }
    if (root == null) root = structure.nodes().get(0).id();

    Map<String, Integer> width = new HashMap<>();
    computeWidth(root, children, width);
    placeNode(root, 0, 0, 2 * Math.PI, children, width, positions);
    return positions;
  }

  private int computeWidth(
      String id, Map<String, List<String>> children, Map<String, Integer> width) {
    List<String> ch = children.getOrDefault(id, List.of());
    if (ch.isEmpty()) {
      width.put(id, 1);
      return 1;
    }
    int total = 0;
    for (String c : ch) total += computeWidth(c, children, width);
    width.put(id, total);
    return total;
  }

  private void placeNode(
      String id,
      int depth,
      double angleStart,
      double angleEnd,
      Map<String, List<String>> children,
      Map<String, Integer> width,
      Map<String, Vec3> positions) {
    double angle = (angleStart + angleEnd) / 2.0;
    double radius = depth * BASE_RADIUS;
    positions.put(id, Vec3.of(radius * Math.cos(angle), -depth * Y_STEP, radius * Math.sin(angle)));

    List<String> ch = children.getOrDefault(id, List.of());
    if (ch.isEmpty()) return;
    int totalWidth = width.get(id);
    double cursor = angleStart;
    for (String child : ch) {
      int childWidth = width.getOrDefault(child, 1);
      double childEnd = cursor + (angleEnd - angleStart) * childWidth / totalWidth;
      placeNode(child, depth + 1, cursor, childEnd, children, width, positions);
      cursor = childEnd;
    }
  }
}
