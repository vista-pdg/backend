package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.impl.AvlInsertAlgorithm;
import com.vista.pdg.service.algorithm.impl.GraphBfsAlgorithm;
import com.vista.pdg.service.algorithm.impl.InorderTraversalAlgorithm;
import com.vista.pdg.service.algorithm.impl.QueueDequeueAlgorithm;
import com.vista.pdg.service.algorithm.impl.StackPopAlgorithm;
import com.vista.pdg.service.layout.impl.LinearLayout3D;
import com.vista.pdg.service.layout.impl.StackLayout3D;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-22b: variables y pila de llamadas en el rastro; código en las cuatro familias. */
class InstrumentedTracesTest {

  private final InorderTraversalAlgorithm inorder =
      new InorderTraversalAlgorithm(new TreeLayout3D());
  private final GraphBfsAlgorithm bfs = new GraphBfsAlgorithm();
  private final StackPopAlgorithm pop = new StackPopAlgorithm(new StackLayout3D());
  private final QueueDequeueAlgorithm dequeue = new QueueDequeueAlgorithm(new LinearLayout3D());
  private final AvlInsertAlgorithm avl =
      new AvlInsertAlgorithm(new AvlStepsService(new TreeLayout3D()));

  private static Node3D node(String id, String label) {
    return new Node3D(id, label, 0, 0, 0, 0, null, Map.of());
  }

  private static Edge3D edge(String a, String b) {
    return new Edge3D("e-" + a + "-" + b, a, b, null, false);
  }

  // ── CA-3: pila de llamadas ─────────────────────────────────────────────

  @Test
  @DisplayName(
      "CA-3: entrar en una llamada anidada apila un marco con su parámetro y retornar lo retira")
  void callStackGrowsAndShrinks() {
    List<AlgorithmStep> steps =
        inorder
            .generate(new AlgorithmRequest("tree", "bst", "inorder", List.of(10, 5, 15)))
            .steps();
    AlgorithmStep first = steps.getFirst();
    assertThat(first.callStack()).hasSize(1);
    assertThat(first.callStack().getFirst().name()).isEqualTo("inorden");
    assertThat(first.callStack().getFirst().params()).containsEntry("nodo", "10");

    // Línea 4 sobre la raíz: se acaba de apilar inorden(5) encima de inorden(10).
    AlgorithmStep callLeft =
        steps.stream()
            .filter(
                s ->
                    s.line() != null && s.line() == 4 && s.highlightedNodeIds().contains("node-10"))
            .findFirst()
            .orElseThrow();
    assertThat(callLeft.callStack())
        .extracting(f -> f.params().get("nodo"))
        .containsExactly("10", "5");

    // Dentro de inorden(5) → inorden(nulo) por la izquierda: tres marcos, el tope con «nulo».
    AlgorithmStep nullCheck =
        steps.stream()
            .filter(s -> s.line() != null && s.line() == 2 && s.callStack().size() == 3)
            .findFirst()
            .orElseThrow();
    assertThat(nullCheck.callStack().getLast().params()).containsEntry("nodo", "nulo");
    assertThat(nullCheck.variables()).containsEntry("nodo", "nulo");

    // Tras el retorno de esa llamada (línea 3) el siguiente paso vuelve a dos marcos.
    int i = steps.indexOf(nullCheck);
    assertThat(steps.get(i + 1).line()).isEqualTo(3);
    assertThat(steps.get(i + 2).callStack()).hasSize(2);

    // El último «retornar» de la raíz deja la pila vacía en el resumen.
    assertThat(steps.getLast().callStack()).isEmpty();
    int maxDepth =
        steps.stream()
            .mapToInt(s -> s.callStack() == null ? 0 : s.callStack().size())
            .max()
            .orElse(0);
    assertThat(maxDepth).isEqualTo(3);
  }

  // ── CA-2: variables ────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-2: en cada paso las variables reflejan el nodo apuntado y la salida acumulada")
  void variablesFollowTheTraversal() {
    List<AlgorithmStep> steps =
        inorder
            .generate(new AlgorithmRequest("tree", "bst", "inorder", List.of(10, 5, 15)))
            .steps();
    assertThat(steps.get(3).variables()).containsKeys("nodo", "salida");
    AlgorithmStep visit5 =
        steps.stream()
            .filter(
                s -> s.line() != null && s.line() == 5 && s.highlightedNodeIds().contains("node-5"))
            .findFirst()
            .orElseThrow();
    assertThat(visit5.variables()).containsEntry("nodo", "5").containsEntry("salida", "[5]");
    AlgorithmStep visit15 =
        steps.stream()
            .filter(
                s ->
                    s.line() != null && s.line() == 5 && s.highlightedNodeIds().contains("node-15"))
            .findFirst()
            .orElseThrow();
    assertThat(visit15.variables()).containsEntry("salida", "[5, 10, 15]");
    assertThat(steps.getLast().variables()).containsEntry("salida", "[5, 10, 15]");
    // Las variables van en el orden en que el algoritmo las declara.
    assertThat(visit5.variables().keySet()).containsExactly("nodo", "salida");
  }

  // ── CA-5: código en las cuatro familias ────────────────────────────────

  @Test
  @DisplayName("CA-5: BFS, pop y dequeue traen código, línea y variables sin cambiar su rastro")
  void everyFamilyHasCode() {
    List<Node3D> nodes = List.of(node("a", "A"), node("b", "B"), node("c", "C"));
    List<Edge3D> edges = List.of(edge("a", "b"), edge("b", "c"));
    StepsResponse b =
        bfs.generate(new AlgorithmRequest("graph", "simple", "bfs", null, nodes, edges, "a"));
    assertThat(b.code()).isEqualTo(GraphBfsAlgorithm.CODE);
    assertThat(b.steps()).hasSize(1 + 3 + 2 + 1);
    assertThat(b.steps()).allSatisfy(s -> assertThat(s.line()).isBetween(1, 8));
    assertThat(b.steps())
        .allSatisfy(s -> assertThat(s.variables()).containsKeys("u", "cola", "visitados", "orden"));
    assertThat(b.steps()).allSatisfy(s -> assertThat(s.callStack()).isNull());
    AlgorithmStep visitB =
        b.steps().stream().filter(s -> s.title().equals("Visitar B")).findFirst().orElseThrow();
    assertThat(visitB.variables())
        .containsEntry("u", "B")
        .containsEntry("orden", "[A, B]")
        .containsEntry("cola", "[]");
    AlgorithmStep discoverB =
        b.steps().stream()
            .filter(s -> s.title().equals("Descubrir vecinos de B"))
            .findFirst()
            .orElseThrow();
    assertThat(discoverB.variables())
        .containsEntry("cola", "[C]")
        .containsEntry("visitados", "{A, B, C}");
    assertThat(b.steps().getLast().variables()).containsEntry("orden", "[A, B, C]");

    StepsResponse p = pop.generate(new AlgorithmRequest("stack", "simple", "pop", List.of(3, 42)));
    assertThat(p.code()).isEqualTo(StackPopAlgorithm.CODE);
    assertThat(p.steps()).hasSize(5);
    assertThat(p.steps().get(1).line()).isEqualTo(3);
    assertThat(p.steps().get(1).variables())
        .containsEntry("tope", "42")
        .containsEntry("tamaño", "2")
        .containsEntry("retirados", "[]");
    assertThat(p.steps().get(2).line()).isEqualTo(4);
    assertThat(p.steps().get(2).variables())
        .containsEntry("tamaño", "1")
        .containsEntry("retirados", "[42]");
    assertThat(p.steps().getLast().variables()).containsEntry("retirados", "[42, 3]");

    StepsResponse q =
        dequeue.generate(new AlgorithmRequest("queue", "simple", "dequeue", List.of(5, 9)));
    assertThat(q.code()).isEqualTo(QueueDequeueAlgorithm.CODE);
    assertThat(q.steps().get(1).variables())
        .containsEntry("frente", "5")
        .containsEntry("atendidos", "[]");
    assertThat(q.steps().getLast().variables()).containsEntry("atendidos", "[5, 9]");

    // El AVL de HU-19 sigue sin instrumentar y sigue siendo válido.
    StepsResponse a = avl.generate(new AlgorithmRequest("tree", "avl", "insert", List.of(3, 2, 1)));
    assertThat(a.code()).isNull();
    assertThat(a.steps()).allSatisfy(s -> assertThat(s.line()).isNull());
  }
}
