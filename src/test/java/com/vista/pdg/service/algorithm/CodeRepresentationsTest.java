package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.response.CodeRepresentation;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.impl.*;
import com.vista.pdg.service.layout.impl.LinearLayout3D;
import com.vista.pdg.service.layout.impl.StackLayout3D;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeRepresentationsTest {
  @TempDir Path dir;

  @Test
  void everyInstrumentedStepMapsToAnActualJavaLineAndKeepsLegacyCode() {
    var layout = new TreeLayout3D();
    List<StepsResponse> traces =
        List.of(
            new StackPopAlgorithm(new StackLayout3D())
                .generate(new AlgorithmRequest("stack", "simple", "pop", List.of(3, 42))),
            new QueueDequeueAlgorithm(new LinearLayout3D())
                .generate(new AlgorithmRequest("queue", "simple", "dequeue", List.of(3, 42))),
            new InorderTraversalAlgorithm(layout)
                .generate(new AlgorithmRequest("tree", "bst", "inorder", List.of(10, 5, 15))),
            new AvlInsertAlgorithm(new AvlStepsService(layout))
                .generate(new AlgorithmRequest("tree", "avl", "insert", List.of(10, 3, 5))),
            new GraphBfsAlgorithm()
                .generate(
                    new AlgorithmRequest(
                        "graph",
                        "simple",
                        "bfs",
                        null,
                        List.of(new Node3D("a", "A", 0, 0, 0, 0, null, Map.of())),
                        List.of(),
                        "a")));
    for (var response : traces) {
      assertThat(response.error()).isFalse();
      assertThat(response.language()).isEqualTo("pseudocode");
      assertThat(response.code()).isNotEmpty();
      assertThat(response.representations()).hasSize(1);
      var java = response.representations().getFirst();
      assertThat(java.language()).isEqualTo("java");
      for (var step : response.steps()) {
        if (step.line() == null) continue; // Final inorder summary executes no line.
        assertThat(step.line()).isBetween(1, response.code().size());
        assertThat(java.lineMap().get(step.line()))
            .isNotEmpty()
            .allSatisfy(
                n -> {
                  assertThat(n).isBetween(1, java.code().size());
                  assertThat(java.code().get(n - 1)).isNotBlank();
                });
      }
    }
    assertThat(AlgorithmCode.stack().sourceUrl()).contains("c395fb0");
    assertThat(AlgorithmCode.avl().sourceUrl()).isNull();
    assertMapped(AlgorithmCode.stack(), 3, "pila.peek()");
    assertMapped(AlgorithmCode.stack(), 4, "pila.pop()");
    assertMapped(AlgorithmCode.queue(), 3, "cola.front()");
    assertMapped(AlgorithmCode.queue(), 4, "cola.dequeue()");
    assertMapped(AlgorithmCode.inorder(), 3, "return;");
    assertThat(AlgorithmCode.inorder().lineMap().get(3))
        .doesNotContainAnyElementsOf(AlgorithmCode.inorder().lineMap().get(7));
    assertMapped(AlgorithmCode.inorder(), 5, "salida.add(nodo.valor())");
    assertMapped(AlgorithmCode.bfs(), 5, "orden.add(u)");
    assertMapped(AlgorithmCode.avl(), 7, "return balanceado;");
  }

  @Test
  void avlSingleAndDoubleRotationsMapToTheExecutedDirection() {
    var algorithm = new AvlInsertAlgorithm(new AvlStepsService(new TreeLayout3D()));
    for (var values :
        List.of(List.of(3, 2, 1), List.of(1, 2, 3), List.of(3, 1, 2), List.of(1, 3, 2))) {
      var response = algorithm.generate(new AlgorithmRequest("tree", "avl", "insert", values));
      var java = response.representations().getFirst();
      assertThat(response.steps()).anyMatch(s -> s.highlightType().equals("rotated"));
      for (var step : response.steps()) {
        if (!step.highlightType().equals("rotated")) continue;
        boolean left = step.title().contains("Izquierda");
        assertThat(step.line()).isEqualTo(left ? 5 : 6);
        assertMapped(java, step.line(), left ? "izquierda(Nodo pivote)" : "derecha(Nodo pivote)");
      }
      assertThat(response.steps().getLast().nodes())
          .hasSize(3)
          .allSatisfy(
              n ->
                  assertThat(Math.abs(((Number) n.properties().get("balanceFactor")).intValue()))
                      .isLessThanOrEqualTo(1));
    }
  }

  @Test
  void vistaJavaDownloadsCompileWithJava17WithoutApplicationDependencies() throws Exception {
    for (var listing : List.of(AlgorithmCode.avl(), AlgorithmCode.bfs(), AlgorithmCode.inorder())) {
      Path file = dir.resolve(listing.fileName());
      Files.writeString(file, String.join("\n", listing.code()));
      assertThat(
              ToolProvider.getSystemJavaCompiler()
                  .run(
                      null,
                      null,
                      null,
                      "-proc:none",
                      "--release",
                      "17",
                      "-d",
                      dir.toString(),
                      file.toString()))
          .isZero();
    }
  }

  private void assertMapped(CodeRepresentation representation, int line, String operation) {
    assertThat(representation.lineMap().get(line))
        .anySatisfy(n -> assertThat(representation.code().get(n - 1)).contains(operation));
  }
}
