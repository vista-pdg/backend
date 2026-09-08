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

  /** Separación entre árboles cuando la estructura es un bosque. */
  private static final double FOREST_GAP = 4.0;

  /**
   * Coloca <b>todos</b> los nodos. Un contrato del modelo puede traer varios nodos sin padre (un
   * bosque, o un padre referido por un id inexistente) o un ciclo; antes esos nodos no recibían
   * posición y acababan todos en el origen. Ahora cada raíz se dispone como su propio cono y los
   * cones se reparten en el eje X; lo inalcanzable se trata como una raíz más.
   */
  @Override
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    Map<String, Vec3> positions = new HashMap<>();
    if (structure.nodes().isEmpty()) return positions;

    Map<String, List<String>> children = new LinkedHashMap<>();
    Set<String> hasIncoming = new HashSet<>();
    for (Node3D node : structure.nodes()) children.put(node.id(), new ArrayList<>());
    for (Edge3D edge : structure.edges()) {
      if (!children.containsKey(edge.from()) || !children.containsKey(edge.to())) continue;
      children.get(edge.from()).add(edge.to());
      hasIncoming.add(edge.to());
    }

    List<String> roots = new ArrayList<>();
    for (Node3D node : structure.nodes())
      if (!hasIncoming.contains(node.id())) roots.add(node.id());

    Set<String> placed = new HashSet<>();
    List<String> order = new ArrayList<>(roots);
    // Nodos en ciclos (todos con entrada): se usan como raíces adicionales en orden de aparición.
    for (Node3D node : structure.nodes()) if (!order.contains(node.id())) order.add(node.id());

    double offsetX = 0;
    for (String root : order) {
      if (placed.contains(root)) continue;
      Map<String, Integer> width = new HashMap<>();
      computeWidth(root, children, width, new HashSet<>());
      Map<String, Vec3> local = new HashMap<>();
      placeNode(root, 0, 0, 2 * Math.PI, children, width, local, new HashSet<>());
      double maxRadius = 0;
      for (Vec3 v : local.values()) maxRadius = Math.max(maxRadius, Math.hypot(v.x(), v.z()));
      double shift = offsetX + (offsetX == 0 ? 0 : maxRadius);
      for (Map.Entry<String, Vec3> en : local.entrySet()) {
        if (placed.add(en.getKey())) {
          Vec3 v = en.getValue();
          positions.put(en.getKey(), Vec3.of(v.x() + shift, v.y(), v.z()));
        }
      }
      offsetX = shift + maxRadius + FOREST_GAP;
    }
    return positions;
  }

  private int computeWidth(
      String id,
      Map<String, List<String>> children,
      Map<String, Integer> width,
      Set<String> visiting) {
    if (!visiting.add(id)) return 0; // ciclo: no se vuelve a contar
    List<String> ch = children.getOrDefault(id, List.of());
    int total = 0;
    for (String c : ch) total += computeWidth(c, children, width, visiting);
    if (total == 0) total = 1;
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
      Map<String, Vec3> positions,
      Set<String> visiting) {
    if (!visiting.add(id)) return; // ciclo: ya está colocado más arriba
    double angle = (angleStart + angleEnd) / 2.0;
    double radius = depth * BASE_RADIUS;
    positions.put(id, Vec3.of(radius * Math.cos(angle), -depth * Y_STEP, radius * Math.sin(angle)));

    List<String> ch = children.getOrDefault(id, List.of());
    if (ch.isEmpty()) return;
    int totalWidth = Math.max(1, width.getOrDefault(id, 1));
    double cursor = angleStart;
    for (String child : ch) {
      int childWidth = width.getOrDefault(child, 1);
      double childEnd = cursor + (angleEnd - angleStart) * childWidth / totalWidth;
      placeNode(child, depth + 1, cursor, childEnd, children, width, positions, visiting);
      cursor = childEnd;
    }
  }
}
