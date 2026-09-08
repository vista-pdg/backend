package com.vista.pdg.service.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TreeLayout3DTest {

  private static Node3D node(String id, String parent, int depth) {
    return new Node3D(id, id, 0, 0, 0, depth, parent, Map.of());
  }

  private static Edge3D edge(String a, String b) {
    return new Edge3D("e-" + a + "-" + b, a, b, null, true);
  }

  private static double dist(Vec3 a, Vec3 b) {
    return Math.sqrt(
        Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
  }

  @Test
  @DisplayName("un árbol normal: raíz en el origen, cada nivel más abajo, sin dos nodos iguales")
  void singleTree() {
    List<Node3D> nodes =
        List.of(node("a", null, 0), node("b", "a", 1), node("c", "a", 1), node("d", "b", 2));
    List<Edge3D> edges = List.of(edge("a", "b"), edge("a", "c"), edge("b", "d"));
    Map<String, Vec3> pos =
        new TreeLayout3D().compute(new GeneratedStructure(null, nodes, edges, Map.of()));
    assertThat(pos).hasSize(4);
    assertThat(pos.get("a")).isEqualTo(Vec3.zero());
    assertThat(pos.get("b").y()).isLessThan(pos.get("a").y());
    assertThat(pos.get("d").y()).isLessThan(pos.get("b").y());
    assertThat(dist(pos.get("b"), pos.get("c"))).isGreaterThan(1);
  }

  @Test
  @DisplayName("un bosque coloca todos los nodos y separa los árboles en X")
  void forestPlacesEveryNode() {
    List<Node3D> nodes =
        List.of(
            node("a", null, 0),
            node("b", "a", 1),
            node("c", null, 0),
            node("d", "c", 1),
            node("e", null, 0));
    List<Edge3D> edges = List.of(edge("a", "b"), edge("c", "d"));
    Map<String, Vec3> pos =
        new TreeLayout3D().compute(new GeneratedStructure(null, nodes, edges, Map.of()));
    assertThat(pos).hasSize(5);
    assertThat(pos.get("a").x()).isLessThan(pos.get("c").x());
    assertThat(pos.get("c").x()).isLessThan(pos.get("e").x());
    for (String i : List.of("a", "b", "c", "d", "e"))
      for (String j : List.of("a", "b", "c", "d", "e"))
        if (!i.equals(j))
          assertThat(dist(pos.get(i), pos.get(j))).as("%s vs %s", i, j).isGreaterThan(1);
  }

  @Test
  @DisplayName("un ciclo no cuelga la disposición y cada nodo recibe una posición")
  void cycleTerminates() {
    List<Node3D> nodes = List.of(node("a", null, 0), node("b", "a", 1), node("c", "b", 2));
    List<Edge3D> edges = List.of(edge("a", "b"), edge("b", "c"), edge("c", "a"));
    Map<String, Vec3> pos =
        new TreeLayout3D().compute(new GeneratedStructure(null, nodes, edges, Map.of()));
    assertThat(pos).hasSize(3);
  }

  @Test
  @DisplayName("fillMissing coloca en rejilla lo que la estrategia no colocó, lejos de lo colocado")
  void fillMissingSpreadsLeftovers() {
    List<Node3D> nodes = List.of(node("a", null, 0), node("b", null, 0), node("c", null, 0));
    Map<String, Vec3> pos = new HashMap<>(Map.of("a", Vec3.of(1, 1, 0)));
    Map<String, Vec3> filled =
        LayoutDispatcher.fillMissing(new GeneratedStructure(null, nodes, List.of(), Map.of()), pos);
    assertThat(filled).hasSize(3);
    assertThat(dist(filled.get("b"), filled.get("c"))).isGreaterThan(1);
    assertThat(dist(filled.get("a"), filled.get("b"))).isGreaterThan(1);
  }
}
