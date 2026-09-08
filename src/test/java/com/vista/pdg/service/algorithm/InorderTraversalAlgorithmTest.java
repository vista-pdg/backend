package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.impl.InorderTraversalAlgorithm;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-22a: rastro instrumentado línea por línea del recorrido inorden. */
class InorderTraversalAlgorithmTest {

  private final InorderTraversalAlgorithm inorder =
      new InorderTraversalAlgorithm(new TreeLayout3D());

  private static AlgorithmRequest values(Integer... v) {
    return new AlgorithmRequest("tree", "bst", "inorder", List.of(v));
  }

  private static Node3D node(String id, String label, String parent, int depth) {
    return new Node3D(id, label, 1, 2, 3, depth, parent, Map.of());
  }

  @Test
  @DisplayName("la respuesta lleva el pseudocódigo y cada paso una línea válida")
  void codeAndLines() {
    StepsResponse r = inorder.generate(values(10, 5, 15));
    assertThat(r.error()).isFalse();
    assertThat(r.code()).isEqualTo(InorderTraversalAlgorithm.CODE).hasSize(7);
    assertThat(r.language()).isEqualTo("pseudocode");
    List<AlgorithmStep> steps = r.steps();
    assertThat(steps).allSatisfy(s -> assertThat(s.index()).isEqualTo(steps.indexOf(s)));
    assertThat(steps.subList(0, steps.size() - 1))
        .allSatisfy(s -> assertThat(s.line()).isBetween(1, 7));
    assertThat(steps.getFirst().line()).isEqualTo(1);
    assertThat(steps.getLast().line()).isNull();
    assertThat(steps.getLast().highlightType()).isEqualTo("done");
  }

  @Test
  @DisplayName("CA-6: las visitas salen en orden y el resumen final lista la secuencia inorden")
  void visitsAreSorted() {
    List<AlgorithmStep> steps = inorder.generate(values(8, 3, 10, 1, 6, 14, 4, 7)).steps();
    List<String> visits =
        steps.stream()
            .filter(s -> s.highlightType().equals("visit"))
            .map(s -> s.highlightedNodeIds().getFirst())
            .toList();
    assertThat(visits)
        .containsExactly(
            "node-1", "node-3", "node-4", "node-6", "node-7", "node-8", "node-10", "node-14");
    assertThat(steps.stream().filter(s -> s.highlightType().equals("visit")))
        .allMatch(s -> s.line() == 5);
    assertThat(steps.getLast().description()).contains("[1, 3, 4, 6, 7, 8, 10, 14]");
    assertThat(steps.getLast().nodes())
        .extracting(n -> n.properties().get("state"))
        .containsOnly("visited");
  }

  @Test
  @DisplayName("CA-1: la línea 'visitar' y el nodo resaltado pertenecen al mismo paso")
  void lineAndNodeInSameStep() {
    List<AlgorithmStep> steps = inorder.generate(values(10, 5, 15)).steps();
    AlgorithmStep first5 =
        steps.stream().filter(s -> s.line() != null && s.line() == 5).findFirst().orElseThrow();
    assertThat(first5.highlightedNodeIds()).containsExactly("node-5");
    assertThat(
            first5.nodes().stream()
                .filter(n -> n.id().equals("node-5"))
                .findFirst()
                .orElseThrow()
                .properties())
        .containsEntry("state", "current");
    // Un paso por línea ejecutada: para 3 nodos, 6 llamadas a nulo y 3 visitas.
    long nullReturns = steps.stream().filter(s -> s.line() != null && s.line() == 3).count();
    assertThat(nullReturns).isEqualTo(4);
  }

  @Test
  @DisplayName("recorre el árbol del lienzo cuando llega en la petición y respeta sus posiciones")
  void usesCanvasTree() {
    List<Node3D> nodes =
        List.of(
            node("a", "10", null, 0),
            node("b", "5", "a", 1),
            node("c", "15", "a", 1),
            node("d", "7", "b", 2));
    List<Edge3D> edges =
        List.of(
            new Edge3D("e1", "a", "b", null, true),
            new Edge3D("e2", "a", "c", null, true),
            new Edge3D("e3", "b", "d", null, true));
    List<AlgorithmStep> steps =
        inorder
            .generate(new AlgorithmRequest("tree", "bst", "inorder", null, nodes, edges, null))
            .steps();
    List<String> visits =
        steps.stream()
            .filter(s -> s.highlightType().equals("visit"))
            .map(s -> s.highlightedNodeIds().getFirst())
            .toList();
    assertThat(visits).containsExactly("b", "d", "a", "c");
    assertThat(steps.getFirst().nodes()).allMatch(n -> n.x() == 1 && n.y() == 2 && n.z() == 3);
    assertThat(steps.getFirst().edges()).isEqualTo(edges);
  }

  @Test
  @DisplayName("sin árbol ni valores devuelve error; un nodo aislado es un árbol de uno")
  void errorsAndDegenerate() {
    assertThat(inorder.generate(new AlgorithmRequest("tree", "bst", "inorder", List.of())).error())
        .isTrue();
    StepsResponse one = inorder.generate(values(42));
    assertThat(one.error()).isFalse();
    assertThat(one.steps().getLast().description()).contains("[42]");
    assertThat(inorder.generate(values(2, 1, 3, 2)).steps().getLast().description())
        .contains("[1, 2, 3]");
  }
}
