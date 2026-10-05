package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.contract.*;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import com.vista.pdg.service.algorithm.impl.*;
import com.vista.pdg.service.generator.impl.*;
import com.vista.pdg.service.generator.impl.graph.TreeGenerator;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.util.*;
import org.junit.jupiter.api.Test;

class BasicStructureAlgorithmsTest {
  private AlgorithmRequest request(AlgorithmStrategy a, GeneratedStructure s, Double argument) {
    var d = a.descriptor();
    return new AlgorithmRequest(
        d.type(), d.subtype(), d.operation(), null, s.nodes(), s.edges(), null, argument);
  }

  private AlgorithmRequest values(AlgorithmStrategy a, List<Integer> values) {
    var d = a.descriptor();
    return new AlgorithmRequest(d.type(), d.subtype(), d.operation(), values);
  }

  private GeneratedStructure tree() {
    return new TreeGenerator()
        .generate(
            new TreeContract(
                "tree",
                null,
                "bst",
                List.of(new TreeContract.Operation("insert", List.of(10, 5, 15, 3, 7))),
                null));
  }

  private GeneratedStructure list(String subtype, List<Integer> values) {
    return new LinkedListGenerator()
        .generate(new LinkedListContract("linked-list", null, subtype, values));
  }

  private GeneratedStructure scene(AlgorithmStep s) {
    return new GeneratedStructure(null, s.nodes(), s.edges(), Map.of());
  }

  private AlgorithmStep end(StepsResponse r) {
    assertThat(r.error()).isFalse();
    for (var s : r.steps()) {
      assertThat(s.line()).isBetween(1, r.code().size());
      assertThat(s.variables()).isNotEmpty();
      assertThat(s.nodes()).extracting(Node3D::id).doesNotHaveDuplicates();
      var ids = s.nodes().stream().map(Node3D::id).toList();
      s.edges().forEach(e -> assertThat(ids).contains(e.from(), e.to()));
    }
    return r.steps().getLast();
  }

  @Test
  void treeTraversalsRespectShapeAndExposeRecursion() {
    var source = tree();
    var shuffled =
        new GeneratedStructure(
            null, source.nodes().reversed(), source.edges().reversed(), Map.of());
    for (AlgorithmStrategy a :
        List.of(new TreeBasicsAlgorithms.BstPreorder(), new TreeBasicsAlgorithms.AvlPreorder())) {
      var r = a.generate(request(a, shuffled, null));
      assertThat(end(r).variables()).containsEntry("salida", "[10, 5, 3, 7, 15]");
      assertThat(r.steps()).anyMatch(s -> s.callStack().size() > 1);
      assertThat(end(r).nodes())
          .extracting(Node3D::id)
          .containsExactlyElementsOf(shuffled.nodes().stream().map(Node3D::id).toList());
    }
    for (AlgorithmStrategy a :
        List.of(new TreeBasicsAlgorithms.BstPostorder(), new TreeBasicsAlgorithms.AvlPostorder()))
      assertThat(end(a.generate(request(a, source, null))).variables())
          .containsEntry("salida", "[3, 7, 5, 15, 10]");
    for (AlgorithmStrategy a :
        List.of(new TreeBasicsAlgorithms.BstLevels(), new TreeBasicsAlgorithms.AvlLevels()))
      assertThat(end(a.generate(request(a, source, null))).variables())
          .containsEntry("salida", "[10, 5, 15, 3, 7]");
  }

  @Test
  void treeSearchFindsExtremesAndAbsenceAndRejectsInvalidArguments() {
    for (AlgorithmStrategy a :
        List.of(new TreeBasicsAlgorithms.BstSearch(), new TreeBasicsAlgorithms.AvlSearch())) {
      assertThat(end(a.generate(request(a, tree(), 7d))).variables())
          .containsEntry("encontrado", "true");
      assertThat(end(a.generate(request(a, tree(), 6d))).variables())
          .containsEntry("encontrado", "false");
      for (Double value :
          new Double[] {null, 1.5, Double.NaN, Double.POSITIVE_INFINITY, 2147483648d})
        assertThatThrownBy(() -> a.generate(request(a, tree(), value)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    var s =
        new TreeGenerator()
            .generate(
                new TreeContract(
                    "tree",
                    null,
                    "bst",
                    List.of(
                        new TreeContract.Operation(
                            "insert", List.of(0, Integer.MIN_VALUE, Integer.MAX_VALUE))),
                    null));
    var a = new TreeBasicsAlgorithms.BstSearch();
    assertThat(end(a.generate(request(a, s, (double) Integer.MIN_VALUE))).variables())
        .containsEntry("encontrado", "true");
  }

  @Test
  void validatesTreeTopologyAndBstBoundsBeforeRecursing() {
    AlgorithmStrategy a = new TreeBasicsAlgorithms.BstSearch();
    var s = tree();
    List<Edge3D> cycle = new ArrayList<>(s.edges());
    cycle.add(new Edge3D("bad", s.nodes().get(2).id(), s.nodes().getFirst().id(), null, true));
    assertThatThrownBy(
            () ->
                a.generate(
                    request(a, new GeneratedStructure(null, s.nodes(), cycle, Map.of()), 3d)))
        .isInstanceOf(IllegalArgumentException.class);
    var bad =
        s.nodes().stream()
            .map(
                n ->
                    n.label().equals("7")
                        ? new Node3D(n.id(), "17", 0, 0, 0, n.depth(), n.parent(), Map.of())
                        : n)
            .toList();
    assertThatThrownBy(
            () ->
                a.generate(request(a, new GeneratedStructure(null, bad, s.edges(), Map.of()), 17d)))
        .isInstanceOf(IllegalArgumentException.class);
    var disconnected = new ArrayList<>(s.nodes());
    disconnected.add(new Node3D("extra", "9", 0, 0, 0, 0, null, Map.of()));
    assertThatThrownBy(
            () ->
                a.generate(
                    request(
                        a, new GeneratedStructure(null, disconnected, s.edges(), Map.of()), 9d)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void btreeAlgorithmsDoNotAssumeBinarySearchShape() {
    var defs =
        List.of(
            new TreeContract.NodeDef("root", 8, null, null),
            new TreeContract.NodeDef("a", 4, "root", null),
            new TreeContract.NodeDef("b", 6, "root", null),
            new TreeContract.NodeDef("c", 12, "root", null));
    var s = new TreeGenerator().generate(new TreeContract("tree", null, "btree", null, defs));
    AlgorithmStrategy levels = new TreeBasicsAlgorithms.BtreeLevels(),
        search = new TreeBasicsAlgorithms.BtreeSearch();
    assertThat(end(levels.generate(request(levels, s, null))).variables())
        .containsEntry("salida", "[8, 4, 6, 12]");
    assertThat(end(search.generate(request(search, s, 12d))).variables())
        .containsEntry("encontrado", "true");
    assertThat(end(search.generate(request(search, s, 99d))).variables())
        .containsEntry("encontrado", "false");
  }

  @Test
  void bstConstructionIgnoresDuplicatesAndKeepsElementIdsAcrossInsertions() {
    var a = new BstInsertAlgorithm(new TreeGenerator(), new TreeLayout3D());
    var r = a.generate(values(a, List.of(10, 15, 5, 3, 10)));
    assertThat(end(r).nodes()).extracting(Node3D::label).containsExactly("10", "5", "3", "15");
    for (var step : r.steps())
      for (var n : step.nodes()) assertThat(n.id()).isEqualTo("bst:" + n.label());
  }

  @Test
  void stackAndQueueBuildQueriesRespectLifoFifoWithoutRemovingElements() {
    for (boolean stack : List.of(true, false)) {
      AlgorithmStrategy
          build = stack ? new LinearBasicsAlgorithms.Push() : new LinearBasicsAlgorithms.Enqueue(),
          peek =
              stack
                  ? new LinearBasicsAlgorithms.StackPeek()
                  : new LinearBasicsAlgorithms.QueuePeek();
      var r = build.generate(values(build, List.of(3, 8, 2)));
      assertThat(r.steps().getFirst().nodes()).isEmpty();
      var s = scene(end(r));
      var read = peek.generate(request(peek, s, null));
      assertThat(end(read).variables()).containsEntry("resultado", stack ? "2" : "3");
      assertThat(end(read).nodes()).extracting(Node3D::label).containsExactly("3", "8", "2");
      assertThat(end(read).nodes())
          .extracting(Node3D::id)
          .containsExactlyElementsOf(s.nodes().stream().map(Node3D::id).toList());
    }
  }

  @Test
  void listsFollowLinksRatherThanArrayOrderAndTerminateOnCircularLists() {
    for (String subtype : List.of("singly", "doubly", "circular")) {
      var s = list(subtype, List.of(2, 3, 2));
      AlgorithmStrategy walk = new LinearBasicsAlgorithms.TraverseList(),
          search = new LinearBasicsAlgorithms.SearchList(),
          reverse = new LinearBasicsAlgorithms.ReverseList();
      assertThat(end(walk.generate(request(walk, s, null))).variables())
          .containsEntry("salida", "[2, 3, 2]");
      assertThat(end(search.generate(request(search, s, 3d))).highlightedNodeIds())
          .containsExactly("n1");
      assertThat(end(search.generate(request(search, s, 8d))).variables())
          .containsEntry("encontrado", "false");
      assertThat(end(reverse.generate(request(reverse, s, null))).nodes())
          .extracting(Node3D::id)
          .containsExactly("n2", "n1", "n0");
    }
    var s = list("singly", List.of(1, 2, 3));
    var shuffled = new GeneratedStructure(null, s.nodes().reversed(), s.edges(), Map.of());
    AlgorithmStrategy walk = new LinearBasicsAlgorithms.TraverseList();
    assertThat(end(walk.generate(request(walk, shuffled, null))).variables())
        .containsEntry("salida", "[1, 2, 3]");
  }

  @Test
  void listMutationsPreserveTypesDuplicatesIdsAndInput() {
    for (String subtype : List.of("singly", "doubly", "circular")) {
      var s = list(subtype, List.of(2, 3, 2));
      AlgorithmStrategy append = new LinearBasicsAlgorithms.AppendList(),
          delete = new LinearBasicsAlgorithms.DeleteList(),
          walk = new LinearBasicsAlgorithms.TraverseList();
      var added = end(append.generate(request(append, s, 9d)));
      assertThat(added.nodes()).extracting(Node3D::label).containsExactly("2", "3", "2", "9");
      assertThat(end(walk.generate(request(walk, scene(added), null))).variables())
          .containsEntry("salida", "[2, 3, 2, 9]");
      var removed = end(delete.generate(request(delete, s, 2d)));
      assertThat(removed.nodes()).extracting(Node3D::id).containsExactly("n1", "n2");
      assertThat(end(walk.generate(request(walk, scene(removed), null))).variables())
          .containsEntry("salida", "[3, 2]");
      assertThat(end(delete.generate(request(delete, s, 99d))).nodes())
          .extracting(Node3D::id)
          .containsExactly("n0", "n1", "n2");
      assertThat(s.nodes()).extracting(Node3D::label).containsExactly("2", "3", "2");
    }
    AlgorithmStrategy delete = new LinearBasicsAlgorithms.DeleteList();
    assertThat(end(delete.generate(request(delete, list("singly", List.of(1)), 1d))).nodes())
        .isEmpty();
  }

  @Test
  void smallListsRetainTheirSubtypeAndRejectIndexesThatDisagreeWithLinks() {
    AlgorithmStrategy append = new LinearBasicsAlgorithms.AppendList(),
        walk = new LinearBasicsAlgorithms.TraverseList();
    for (String subtype : List.of("singly", "doubly", "circular")) {
      for (int count : List.of(1, 2)) {
        var input = list(subtype, count == 1 ? List.of(1) : List.of(1, 2));
        var result = end(append.generate(request(append, input, 3d)));
        int expected =
            subtype.equals("doubly") ? 2 * count : subtype.equals("circular") ? count + 1 : count;
        assertThat(result.edges()).hasSize(expected);
        assertThat(result.nodes())
            .allSatisfy(n -> assertThat(n.properties()).containsEntry("listSubtype", subtype));
        assertThat(end(walk.generate(request(walk, scene(result), null))).nodes())
            .hasSize(count + 1);
      }
    }
    var s = list("singly", List.of(1, 2, 3));
    List<Node3D> indexed = new ArrayList<>();
    for (int i = 0; i < s.nodes().size(); i++) {
      var n = s.nodes().get(i);
      indexed.add(
          new Node3D(
              n.id(), n.label(), n.x(), n.y(), n.z(), n.depth(), n.parent(), Map.of("index", i)));
    }
    var broken = new GeneratedStructure(null, indexed, List.of(), Map.of());
    assertThatThrownBy(() -> walk.generate(request(walk, broken, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void heapsMaintainCompletenessAndMaximumThroughBuildInsertExtractAndPeek() {
    AlgorithmStrategy build = new HeapBasicsAlgorithms.Heapify(),
        insert = new HeapBasicsAlgorithms.Insert(),
        extract = new HeapBasicsAlgorithms.Extract(),
        peek = new HeapBasicsAlgorithms.Peek();
    var s = scene(end(build.generate(values(build, List.of(3, 1, 8, 8, -2)))));
    assertMaxHeap(s.nodes());
    assertThat(end(peek.generate(request(peek, s, null))).variables()).containsEntry("máximo", "8");
    s = scene(end(insert.generate(request(insert, s, 12d))));
    assertMaxHeap(s.nodes());
    assertThat(s.nodes().getFirst().label()).isEqualTo("12");
    var removed = end(extract.generate(request(extract, s, null)));
    assertThat(removed.variables()).containsEntry("extraído", "12");
    assertMaxHeap(removed.nodes());
    s = scene(end(build.generate(values(build, List.of(7)))));
    assertThat(end(extract.generate(request(extract, s, null))).nodes()).isEmpty();
    var wrong = tree();
    assertThatThrownBy(() -> peek.generate(request(peek, wrong, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertMaxHeap(List<Node3D> nodes) {
    for (int i = 1; i < nodes.size(); i++) {
      assertThat(Integer.parseInt(nodes.get((i - 1) / 2).label()))
          .isGreaterThanOrEqualTo(Integer.parseInt(nodes.get(i).label()));
      assertThat(nodes.get(i).parent()).isEqualTo(nodes.get((i - 1) / 2).id());
    }
  }

  @Test
  void hashSearchAndMutationsHandleNegativesCollisionsAndFirstDuplicate() {
    var s =
        new HashTableGenerator()
            .generate(
                new HashTableContract(
                    "hash-table", null, 3, List.of(-2, 5, 8, 5, Integer.MIN_VALUE), "modular"));
    AlgorithmStrategy search = new HashBasicsAlgorithms.Search(),
        insert = new HashBasicsAlgorithms.Insert(),
        delete = new HashBasicsAlgorithms.Delete();
    assertThat(end(search.generate(request(search, s, -2d))).variables())
        .containsEntry("bucket", "2")
        .containsEntry("encontrado", "true");
    assertThat(end(search.generate(request(search, s, 99d))).variables())
        .containsEntry("encontrado", "false");
    var added = end(insert.generate(request(insert, s, 11d)));
    assertThat(end(search.generate(request(search, scene(added), 11d))).variables())
        .containsEntry("encontrado", "true");
    var removed = end(delete.generate(request(delete, s, 5d)));
    assertThat(removed.nodes()).filteredOn(n -> n.label().equals("5")).hasSize(1);
    assertThat(end(search.generate(request(search, scene(removed), 8d))).variables())
        .containsEntry("encontrado", "true");
    assertThat(end(delete.generate(request(delete, s, 99d))).nodes()).hasSize(s.nodes().size());
    assertThat(s.nodes()).hasSize(8);
  }

  @Test
  void malformedSequenceAndHashInputsAreRejectedInsteadOfLooping() {
    AlgorithmStrategy list = new LinearBasicsAlgorithms.TraverseList();
    var s = list("singly", List.of(1, 2, 3));
    var edges =
        List.of(new Edge3D("a", "n0", "n1", null, true), new Edge3D("b", "n1", "n0", null, true));
    assertThatThrownBy(
            () ->
                list.generate(
                    request(list, new GeneratedStructure(null, s.nodes(), edges, Map.of()), null)))
        .isInstanceOf(IllegalArgumentException.class);
    AlgorithmStrategy hash = new HashBasicsAlgorithms.Search();
    assertThatThrownBy(() -> hash.generate(request(hash, tree(), 1d)))
        .isInstanceOf(IllegalArgumentException.class);
    var bad =
        new GeneratedStructure(
            null,
            List.of(
                new Node3D("a", "1", 0, 0, 0, 0, null, Map.of("index", 0)),
                new Node3D("a", "2", 0, 0, 0, 0, null, Map.of("index", 0))),
            List.of(),
            Map.of());
    assertThatThrownBy(() -> list.generate(request(list, bad, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
