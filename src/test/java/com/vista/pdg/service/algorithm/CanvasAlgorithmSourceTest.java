package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.contract.*;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import com.vista.pdg.service.algorithm.impl.*;
import com.vista.pdg.service.generator.impl.*;
import com.vista.pdg.service.generator.impl.graph.TreeGenerator;
import com.vista.pdg.service.layout.impl.*;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CanvasAlgorithmSourceTest {
  private AlgorithmRequest request(AlgorithmStrategy a, GeneratedStructure s, Double argument) {
    var d = a.descriptor();
    // An example sent accidentally must not replace the supplied canvas.
    return new AlgorithmRequest(
        d.type(), d.subtype(), d.operation(), List.of(999), s.nodes(), s.edges(), null, argument);
  }

  private void originalFirst(StepsResponse r, GeneratedStructure s) {
    assertThat(r.error()).isFalse();
    assertThat(r.steps().getFirst().nodes())
        .usingRecursiveComparison()
        .ignoringFields("properties")
        .isEqualTo(s.nodes());
    assertThat(r.steps().getFirst().edges()).isEqualTo(s.edges());
    for (var step : r.steps()) {
      assertThat(step.nodes()).extracting(Node3D::id).doesNotHaveDuplicates();
      var ids = step.nodes().stream().map(Node3D::id).toList();
      step.edges().forEach(e -> assertThat(ids).contains(e.from(), e.to()));
      assertThat(ids).containsAll(step.highlightedNodeIds());
    }
  }

  @Test
  void popAndDequeueUseExistingOrderAndKeepSurvivingIdsWithDuplicateValues() {
    var stack = new StackGenerator().generate(new StackContract("stack", null, List.of(3, 7, 3)));
    var queue = new QueueGenerator().generate(new QueueContract("queue", null, List.of(3, 7, 3)));
    var pop = new StackPopAlgorithm(new StackLayout3D());
    var dequeue = new QueueDequeueAlgorithm(new LinearLayout3D());
    for (var pair : List.of(Map.entry(pop, stack), Map.entry(dequeue, queue))) {
      var result = pair.getKey().generate(request(pair.getKey(), pair.getValue(), null));
      originalFirst(result, pair.getValue());
      assertThat(result.steps().getLast().nodes()).isEmpty();
      assertThat(result.steps()).hasSize(7);
      String retiredId = pair.getKey() == pop ? "n2" : "n0";
      assertThat(result.steps().get(2).nodes()).extracting(Node3D::id).doesNotContain(retiredId);
    }
  }

  @Test
  void pushAndEnqueueAppendToTheCanvasInsteadOfRebuildingFromExamples() {
    for (AlgorithmStrategy a :
        List.of(new LinearBasicsAlgorithms.Push(), new LinearBasicsAlgorithms.Enqueue())) {
      var s =
          a.descriptor().type().equals("stack")
              ? new StackGenerator().generate(new StackContract("stack", null, List.of(3, 7)))
              : new QueueGenerator().generate(new QueueContract("queue", null, List.of(3, 7)));
      var r = a.generate(request(a, s, 42d));
      originalFirst(r, s);
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::label)
          .containsExactly("3", "7", "42");
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::id)
          .containsAll(s.nodes().stream().map(Node3D::id).toList());
    }
  }

  static Stream<AlgorithmStrategy> sorting() {
    return Stream.of(
        new SortingAlgorithms.Bubble(),
        new SortingAlgorithms.Selection(),
        new SortingAlgorithms.Insertion(),
        new SortingAlgorithms.Merge(),
        new SortingAlgorithms.Quick(),
        new SortingAlgorithms.Heap());
  }

  @ParameterizedTest
  @MethodSource("sorting")
  void sortsTheExistingListPreservingDuplicateElementIdentity(AlgorithmStrategy a) {
    for (String subtype : List.of("singly", "doubly", "circular")) {
      var s =
          new LinkedListGenerator()
              .generate(new LinkedListContract("linked-list", null, subtype, List.of(8, 3, 3, -1)));
      var r = a.generate(request(a, s, null));
      originalFirst(r, s);
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::label)
          .containsExactly("-1", "3", "3", "8");
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::id)
          .containsExactlyInAnyOrderElementsOf(s.nodes().stream().map(Node3D::id).toList());
    }
  }

  @Test
  void treeInsertionsBeginAtTheOriginalTreeAndPreserveIdsThroughRotations() {
    var generator = new TreeGenerator();
    var layout = new TreeLayout3D();
    var source =
        generator.generate(
            new TreeContract(
                "tree",
                null,
                "bst",
                List.of(new TreeContract.Operation("insert", List.of(10, 5))),
                null));
    for (AlgorithmStrategy a :
        List.of(
            new AvlInsertAlgorithm(new AvlStepsService(layout)),
            new BstInsertAlgorithm(generator, layout))) {
      var r = a.generate(request(a, source, 3d));
      originalFirst(r, source);
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::label)
          .containsExactlyInAnyOrder("10", "5", "3");
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::id)
          .containsAll(source.nodes().stream().map(Node3D::id).toList());
    }
  }

  @Test
  void heapifyUsesOriginalNodesAndRejectsWrongStructures() {
    var source =
        new TreeGenerator()
            .generate(
                new TreeContract(
                    "tree",
                    null,
                    "bst",
                    List.of(new TreeContract.Operation("insert", List.of(3, 1, 8))),
                    null));
    var a = new HeapBasicsAlgorithms.Heapify();
    var r = a.generate(request(a, source, null));
    originalFirst(r, source);
    assertThat(r.steps().getLast().nodes().getFirst().label()).isEqualTo("8");
    assertThat(r.steps().getLast().nodes())
        .extracting(Node3D::id)
        .containsExactlyInAnyOrderElementsOf(source.nodes().stream().map(Node3D::id).toList());
    assertThatThrownBy(
            () -> new StackPopAlgorithm(new StackLayout3D()).generate(request(a, source, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void originalIdsMayCollideWithGeneratedValueBasedIds() {
    var layout = new TreeLayout3D();
    for (AlgorithmStrategy a :
        List.of(
            new AvlInsertAlgorithm(new AvlStepsService(layout)),
            new BstInsertAlgorithm(new TreeGenerator(), layout))) {
      String id = a.descriptor().subtype().equals("avl") ? "node-3" : "bst:3";
      var source =
          new GeneratedStructure(
              null, List.of(new Node3D(id, "10", 0, 0, 0, 0, null, Map.of())), List.of(), Map.of());
      var r = a.generate(request(a, source, 3d));
      originalFirst(r, source);
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::label)
          .containsExactlyInAnyOrder("10", "3");
      assertThat(r.steps().getLast().nodes()).extracting(Node3D::id).contains(id);
    }
  }
}
