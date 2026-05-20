package com.vista.pdg.service.generator.impl.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphGeneratorTest {

  private GraphGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new GraphGenerator();
  }

  @Test
  void undirectedAdjacency_createsCorrectNodesAndEdges() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B", "C"),
            new GraphContract.MatrixDef(
                "adjacency", List.of(List.of(0, 1, 1), List.of(1, 0, 0), List.of(1, 0, 0)), null));

    GeneratedStructure result = generator.generate(g);

    assertThat(result.nodes()).hasSize(3);
    assertThat(result.nodes()).extracting("label").containsExactly("A", "B", "C");
    assertThat(result.edges()).hasSize(2);
    assertThat(result.edges()).allMatch(e -> !e.directed());
  }

  @Test
  void directedAdjacency_traversesFullMatrix() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            true,
            false,
            List.of("A", "B", "C"),
            new GraphContract.MatrixDef(
                "adjacency", List.of(List.of(0, 1, 0), List.of(0, 0, 1), List.of(0, 0, 0)), null));

    GeneratedStructure result = generator.generate(g);

    assertThat(result.edges()).hasSize(2);
    assertThat(result.edges()).allMatch(Edge3D::directed);
  }

  @Test
  void weightedGraph_edgeHasWeight() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            true,
            true,
            List.of("A", "B"),
            new GraphContract.MatrixDef("adjacency", List.of(List.of(0, 5), List.of(0, 0)), null));

    GeneratedStructure result = generator.generate(g);

    assertThat(result.edges()).hasSize(1);
    assertThat(result.edges().get(0).weight()).isEqualTo(5);
  }

  @Test
  void unweightedGraph_edgeWeightIsNull() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B"),
            new GraphContract.MatrixDef("adjacency", List.of(List.of(0, 1), List.of(1, 0)), null));

    GeneratedStructure result = generator.generate(g);

    assertThat(result.edges().get(0).weight()).isNull();
  }

  @Test
  void incidenceMatrix_parsesEdgesCorrectly() {
    // edge0: A-B, edge1: B-C
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B", "C"),
            new GraphContract.MatrixDef(
                "incidence", List.of(List.of(1, 0), List.of(1, 1), List.of(0, 1)), null));

    GeneratedStructure result = generator.generate(g);

    assertThat(result.edges()).hasSize(2);
  }

  @Test
  void isolatedNodes_noEdges() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B"),
            new GraphContract.MatrixDef("adjacency", List.of(List.of(0, 0), List.of(0, 0)), null));

    GeneratedStructure result = generator.generate(g);

    assertThat(result.nodes()).hasSize(2);
    assertThat(result.edges()).isEmpty();
  }
}
