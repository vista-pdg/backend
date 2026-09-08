package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.impl.AvlInsertAlgorithm;
import com.vista.pdg.service.algorithm.impl.GraphBfsAlgorithm;
import com.vista.pdg.service.algorithm.impl.QueueDequeueAlgorithm;
import com.vista.pdg.service.algorithm.impl.StackPopAlgorithm;
import com.vista.pdg.service.layout.impl.LinearLayout3D;
import com.vista.pdg.service.layout.impl.StackLayout3D;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-19: catálogo y algoritmos por familia. Los rastros son deterministas por construcción y estas
 * pruebas fijan su forma, que es lo que el frontend anima en los dos modos.
 */
class AlgorithmStrategiesTest {

  private final GraphBfsAlgorithm bfs = new GraphBfsAlgorithm();
  private final StackPopAlgorithm pop = new StackPopAlgorithm(new StackLayout3D());
  private final QueueDequeueAlgorithm dequeue = new QueueDequeueAlgorithm(new LinearLayout3D());
  private final AvlInsertAlgorithm avl =
      new AvlInsertAlgorithm(new AvlStepsService(new TreeLayout3D()));
  private final AlgorithmDispatcher dispatcher =
      new AlgorithmDispatcher(List.of(pop, bfs, avl, dequeue));

  private static Node3D node(String id, String label) {
    return new Node3D(id, label, 1, 2, 3, 0, null, Map.of());
  }

  private static Edge3D edge(String from, String to, boolean directed) {
    return new Edge3D("e-" + from + "-" + to, from, to, null, directed);
  }

  private static AlgorithmRequest graphRequest(
      List<Node3D> nodes, List<Edge3D> edges, String start) {
    return new AlgorithmRequest("graph", "simple", "bfs", null, nodes, edges, start);
  }

  // ── Catálogo (CA-4) ────────────────────────────────────────────────────

  @Test
  @DisplayName("el catálogo lista un algoritmo por familia, ordenado y con su tipo de entrada")
  void catalogHasOnePerFamily() {
    List<AlgorithmDescriptor> catalog = dispatcher.catalog();
    assertThat(catalog)
        .extracting(AlgorithmDescriptor::key)
        .containsExactly(
            "graph/simple/bfs", "queue/simple/dequeue", "stack/simple/pop", "tree/avl/insert");
    assertThat(catalog)
        .extracting(AlgorithmDescriptor::family)
        .containsExactlyInAnyOrder("graph", "queue", "stack", "tree");
    assertThat(
            catalog.stream()
                .filter(d -> d.key().equals("graph/simple/bfs"))
                .findFirst()
                .orElseThrow()
                .input())
        .isEqualTo("structure");
    assertThat(dispatcher.catalog()).as("estable entre llamadas").isEqualTo(catalog);
  }

  @Test
  @DisplayName("un algoritmo fuera del catálogo devuelve error, no excepción")
  void unknownAlgorithmIsAnError() {
    StepsResponse r = dispatcher.run(new AlgorithmRequest("tree", "bst", "delete", List.of(1)));
    assertThat(r.error()).isTrue();
    assertThat(r.message()).contains("tree/bst/delete");
  }

  @Test
  @DisplayName("el despachador delega en el AVL existente")
  void dispatcherRunsAvl() {
    StepsResponse r =
        dispatcher.run(new AlgorithmRequest("tree", "avl", "insert", List.of(10, 5, 3)));
    assertThat(r.error()).isFalse();
    assertThat(r.steps()).isNotEmpty();
  }

  // ── BFS (CA-3) ─────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "BFS visita por niveles en el orden de las aristas y termina con el recorrido completo")
  void bfsOrderAndSteps() {
    List<Node3D> nodes =
        List.of(node("a", "A"), node("b", "B"), node("c", "C"), node("d", "D"), node("e", "E"));
    List<Edge3D> edges =
        List.of(
            edge("a", "b", false),
            edge("a", "c", false),
            edge("b", "d", false),
            edge("c", "e", false));
    StepsResponse r = bfs.generate(graphRequest(nodes, edges, "a"));

    assertThat(r.error()).isFalse();
    List<AlgorithmStep> steps = r.steps();
    assertThat(steps.getFirst().highlightType()).isEqualTo("frontier");
    assertThat(steps.getFirst().highlightedNodeIds()).containsExactly("a");
    List<String> visitOrder =
        steps.stream()
            .filter(s -> s.highlightType().equals("visit"))
            .map(s -> s.highlightedNodeIds().getFirst())
            .toList();
    assertThat(visitOrder).containsExactly("a", "b", "c", "d", "e");
    AlgorithmStep last = steps.getLast();
    assertThat(last.highlightType()).isEqualTo("done");
    assertThat(last.highlightedNodeIds()).containsExactly("a", "b", "c", "d", "e");
    // 1 inicio + 5 visitas + 3 descubrimientos (a: b,c · b: d · c: e) + 1 final
    assertThat(steps).hasSize(10);
    assertThat(steps).allSatisfy(s -> assertThat(s.index()).isEqualTo(steps.indexOf(s)));
  }

  @Test
  @DisplayName("cada instantánea etiqueta el estado de todos los nodos y conserva sus posiciones")
  void bfsSnapshotsCarryStateAndPositions() {
    List<Node3D> nodes = List.of(node("a", "A"), node("b", "B"), node("c", "C"));
    List<Edge3D> edges = List.of(edge("a", "b", false), edge("b", "c", false));
    List<AlgorithmStep> steps = bfs.generate(graphRequest(nodes, edges, "a")).steps();

    AlgorithmStep visitB =
        steps.stream().filter(s -> s.title().equals("Visitar B")).findFirst().orElseThrow();
    Map<String, Object> stateOf = Map.of();
    assertThat(visitB.nodes())
        .extracting(n -> n.properties().get("state"))
        .containsExactly("visited", "current", "unvisited");
    assertThat(visitB.nodes()).allMatch(n -> n.x() == 1 && n.y() == 2 && n.z() == 3);
    assertThat(visitB.edges()).isEqualTo(edges);
    assertThat(steps.getLast().nodes())
        .extracting(n -> n.properties().get("state"))
        .containsOnly("visited");
    assertThat(stateOf).isEmpty();
  }

  @Test
  @DisplayName("BFS respeta la dirección de las aristas y reporta los nodos no alcanzables")
  void bfsDirectedAndUnreachable() {
    List<Node3D> nodes = List.of(node("a", "A"), node("b", "B"), node("c", "C"));
    List<Edge3D> edges = List.of(edge("a", "b", true), edge("c", "a", true));
    List<AlgorithmStep> steps = bfs.generate(graphRequest(nodes, edges, "a")).steps();
    assertThat(steps.getLast().highlightedNodeIds()).containsExactly("a", "b");
    assertThat(steps.getLast().description()).contains("No alcanzables").contains("C");
  }

  @Test
  @DisplayName("sin nodo inicial válido se arranca en el primero; sin grafo se devuelve error")
  void bfsStartFallbackAndMissingGraph() {
    List<Node3D> nodes = List.of(node("x", "X"), node("y", "Y"));
    assertThat(
            bfs.generate(graphRequest(nodes, List.of(), "nope"))
                .steps()
                .getFirst()
                .highlightedNodeIds())
        .containsExactly("x");
    assertThat(bfs.generate(graphRequest(nodes, null, null)).steps()).hasSize(1 + 1 + 1);
    StepsResponse missing =
        bfs.generate(new AlgorithmRequest("graph", "simple", "bfs", List.of(1)));
    assertThat(missing.error()).isTrue();
    assertThat(missing.message()).contains("lienzo");
  }

  @Test
  @DisplayName(
      "el mismo grafo produce el mismo rastro cada vez (paridad entre modos por construcción)")
  void bfsIsDeterministic() {
    List<Node3D> nodes = List.of(node("a", "A"), node("b", "B"), node("c", "C"), node("d", "D"));
    List<Edge3D> edges =
        List.of(
            edge("a", "b", false),
            edge("a", "c", false),
            edge("b", "d", false),
            edge("c", "d", false));
    assertThat(bfs.generate(graphRequest(nodes, edges, "a")))
        .isEqualTo(bfs.generate(graphRequest(nodes, edges, "a")));
  }

  // ── Pila (CA-5) ────────────────────────────────────────────────────────

  @Test
  @DisplayName("pop emite dos pasos por elemento: tope resaltado y luego retirado")
  void popTwoStepsPerElement() {
    List<AlgorithmStep> steps =
        pop.generate(new AlgorithmRequest("stack", "simple", "pop", List.of(3, 42, 8, 17))).steps();
    assertThat(steps).hasSize(1 + 2 * 4);
    assertThat(steps.get(0).highlightType()).isEqualTo("initial");
    assertThat(steps.get(0).nodes()).hasSize(4);

    AlgorithmStep highlighted = steps.get(1);
    assertThat(highlighted.highlightType()).isEqualTo("pop");
    assertThat(highlighted.highlightedNodeIds()).containsExactly("n3");
    assertThat(highlighted.nodes()).hasSize(4);
    assertThat(highlighted.nodes().getLast().label()).isEqualTo("17");
    assertThat(highlighted.nodes().getLast().properties()).containsEntry("role", "top");

    AlgorithmStep removed = steps.get(2);
    assertThat(removed.nodes()).hasSize(3);
    assertThat(removed.nodes().getLast().label()).isEqualTo("8");
    assertThat(removed.nodes().getLast().properties()).containsEntry("role", "top");
    assertThat(removed.highlightedNodeIds()).containsExactly("n2");

    assertThat(steps.getLast().nodes()).isEmpty();
    assertThat(steps.getLast().description()).contains("vacía");
    // Apilada de verdad: el tope está más arriba que la base.
    assertThat(highlighted.nodes().getLast().y()).isGreaterThan(highlighted.nodes().getFirst().y());
    assertThat(pop.generate(new AlgorithmRequest("stack", "simple", "pop", List.of())).error())
        .isTrue();
  }

  // ── Cola ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("dequeue retira desde el frente, dos pasos por elemento, y reasigna el frente")
  void dequeueFromFront() {
    List<AlgorithmStep> steps =
        dequeue
            .generate(new AlgorithmRequest("queue", "simple", "dequeue", List.of(5, 9, 1)))
            .steps();
    assertThat(steps).hasSize(1 + 2 * 3);
    assertThat(steps.get(1).highlightType()).isEqualTo("dequeue");
    assertThat(steps.get(1).highlightedNodeIds()).containsExactly("n0");
    assertThat(steps.get(1).nodes().getFirst().label()).isEqualTo("5");
    assertThat(steps.get(2).nodes()).extracting(Node3D::label).containsExactly("9", "1");
    assertThat(steps.get(2).nodes().getFirst().properties()).containsEntry("role", "front");
    assertThat(steps.get(2).edges()).hasSize(1);
    assertThat(steps.getLast().nodes()).isEmpty();
    assertThat(dequeue.generate(new AlgorithmRequest("queue", "simple", "dequeue", null)).error())
        .isTrue();
  }
}
