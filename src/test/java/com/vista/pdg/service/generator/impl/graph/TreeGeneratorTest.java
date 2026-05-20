package com.vista.pdg.service.generator.impl.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreeGeneratorTest {

  private TreeGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new TreeGenerator();
  }

  @Test
  void avlInsert_producesCorrectNodeCount() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "avl",
            List.of(new TreeContract.Operation("insert", List.of(10, 5, 15, 3, 7))),
            null);

    GeneratedStructure result = generator.generate(t);

    assertThat(result.nodes()).hasSize(5);
    assertThat(result.edges()).hasSize(4); // tree: n-1 edges
  }

  @Test
  void avlInsert_rootHasNoParent() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "avl",
            List.of(new TreeContract.Operation("insert", List.of(10, 5, 15))),
            null);

    GeneratedStructure result = generator.generate(t);

    long roots = result.nodes().stream().filter(n -> n.parent() == null).count();
    assertThat(roots).isEqualTo(1);
  }

  @Test
  void avlInsert_balanceFactorPresentInProperties() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "avl",
            List.of(new TreeContract.Operation("insert", List.of(10, 5, 15))),
            null);

    GeneratedStructure result = generator.generate(t);

    assertThat(result.nodes()).allMatch(n -> n.properties().containsKey("balanceFactor"));
  }

  @Test
  void bstInsert_maintainsBstOrder() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "bst",
            List.of(new TreeContract.Operation("insert", List.of(10, 5, 15))),
            null);

    GeneratedStructure result = generator.generate(t);

    Node3D root = result.nodes().stream().filter(n -> n.parent() == null).findFirst().orElseThrow();
    assertThat(root.label()).isEqualTo("10");
  }

  @Test
  void heapInsert_rootIsMaxElement() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "heap",
            List.of(new TreeContract.Operation("insert", List.of(3, 10, 5, 1))),
            null);

    GeneratedStructure result = generator.generate(t);

    Node3D root = result.nodes().stream().filter(n -> n.depth() == 0).findFirst().orElseThrow();
    assertThat(Integer.parseInt(root.label())).isEqualTo(10);
  }

  @Test
  void prebuiltNodes_assignsDepthCorrectly() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "bst",
            null,
            List.of(
                new TreeContract.NodeDef("n0", 10, null, null),
                new TreeContract.NodeDef("n1", 5, "n0", "left"),
                new TreeContract.NodeDef("n2", 15, "n0", "right")));

    GeneratedStructure result = generator.generate(t);

    assertThat(result.nodes().stream().filter(n -> n.depth() == 0).count()).isEqualTo(1);
    assertThat(result.nodes().stream().filter(n -> n.depth() == 1).count()).isEqualTo(2);
  }
}
