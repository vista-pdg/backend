package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import com.vista.pdg.service.algorithm.impl.SortingAlgorithms;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SortingAlgorithmsTest {
  static Stream<AlgorithmStrategy> algorithms() {
    return Stream.of(
        new SortingAlgorithms.Bubble(),
        new SortingAlgorithms.Selection(),
        new SortingAlgorithms.Insertion(),
        new SortingAlgorithms.Merge(),
        new SortingAlgorithms.Quick(),
        new SortingAlgorithms.Heap());
  }

  private AlgorithmRequest request(AlgorithmStrategy a, List<Integer> values) {
    var d = a.descriptor();
    return new AlgorithmRequest(d.type(), d.subtype(), d.operation(), values);
  }

  @ParameterizedTest
  @MethodSource("algorithms")
  void sortAdversarialAndSeededInputsWithRealImmutableInstrumentedSteps(AlgorithmStrategy a) {
    List<List<Integer>> inputs =
        new ArrayList<>(
            List.of(
                List.of(4),
                List.of(1, 2, 3, 4),
                List.of(4, 3, 2, 1),
                List.of(3, 1, 3, 1, 0, -1),
                List.of(Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1, Integer.MAX_VALUE),
                Collections.nCopies(8, 2)));
    Random random = new Random(19);
    for (int i = 0; i < 20; i++) {
      List<Integer> values = new ArrayList<>();
      for (int j = 0; j < 1 + random.nextInt(15); j++) values.add(random.nextInt(9) - 4);
      inputs.add(values);
    }
    for (List<Integer> values : inputs) {
      var r = a.generate(request(a, values));
      var expected = values.stream().sorted().map(String::valueOf).toList();
      assertThat(r.error()).isFalse();
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::label)
          .containsExactlyElementsOf(expected);
      assertThat(r.steps().getFirst().nodes())
          .extracting(Node3D::label)
          .containsExactlyElementsOf(values.stream().map(String::valueOf).toList());
      assertThat(r.steps().getLast().callStack()).isEmpty();
      for (int i = 0; i < r.steps().size(); i++) {
        var s = r.steps().get(i);
        assertThat(s.index()).isEqualTo(i);
        assertThat(s.line()).isBetween(1, r.code().size());
        assertThat(s.variables()).isNotEmpty();
        assertThat(s.nodes()).extracting(Node3D::id).doesNotHaveDuplicates().hasSize(values.size());
        assertThat(s.nodes())
            .extracting(Node3D::label)
            .containsExactlyInAnyOrderElementsOf(values.stream().map(String::valueOf).toList());
        var ids = s.nodes().stream().map(Node3D::id).toList();
        s.edges().forEach(e -> assertThat(ids).contains(e.from(), e.to()));
      }
      assertThat(a.generate(request(a, values))).isEqualTo(r);
      assertThatThrownBy(
              () -> r.steps().getFirst().nodes().getFirst().properties().put("state", "tampered"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @ParameterizedTest
  @MethodSource("algorithms")
  void rejectsEmptyAndExcessiveWork(AlgorithmStrategy a) {
    assertThatThrownBy(() -> a.generate(request(a, List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> a.generate(request(a, Collections.nCopies(33, 1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> a.generate(request(a, Arrays.asList(1, null))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stableSortsKeepEqualElementsInOriginalOrder() {
    for (AlgorithmStrategy a :
        List.of(
            new SortingAlgorithms.Bubble(),
            new SortingAlgorithms.Insertion(),
            new SortingAlgorithms.Merge())) {
      var r = a.generate(request(a, List.of(3, 1, 3, 1)));
      assertThat(r.steps().getLast().nodes())
          .extracting(Node3D::id)
          .containsExactly("n1", "n3", "n0", "n2");
    }
  }

  @Test
  void recursiveSortsExposeNestedFramesAndCounters() {
    for (AlgorithmStrategy a :
        List.of(new SortingAlgorithms.Merge(), new SortingAlgorithms.Quick())) {
      var r = a.generate(request(a, List.of(7, 2, 5, 1, 9, 3)));
      assertThat(r.steps()).anyMatch(s -> s.callStack().size() > 1);
      assertThat(Integer.parseInt(r.steps().getLast().variables().get("comparaciones")))
          .isPositive();
    }
    var bubble = new SortingAlgorithms.Bubble();
    var r = bubble.generate(request(bubble, List.of(1, 2, 3, 4)));
    assertThat(r.steps().getLast().variables())
        .containsEntry("comparaciones", "3")
        .containsEntry("intercambios", "0");
  }
}
