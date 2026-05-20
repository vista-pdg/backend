package com.vista.pdg.service.sdd.impl.validator;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.GraphContract;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphContractValidatorTest {

  private GraphContractValidator validator;

  @BeforeEach
  void setUp() {
    validator = new GraphContractValidator();
  }

  // ── Happy paths ───────────────────────────────────────────────────────

  @Test
  void validUndirectedAdjacency_passes() {
    GraphContract g =
        undirected(
            List.of("A", "B", "C"), List.of(List.of(0, 1, 1), List.of(1, 0, 0), List.of(1, 0, 0)));
    assertThatNoException().isThrownBy(() -> validator.validate(g));
  }

  @Test
  void validDirectedAdjacency_passes() {
    GraphContract g = directed(List.of("A", "B"), List.of(List.of(0, 1), List.of(0, 0)));
    assertThatNoException().isThrownBy(() -> validator.validate(g));
  }

  // ── Label checks ──────────────────────────────────────────────────────

  @Test
  void nullLabels_throws() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            null,
            new GraphContract.MatrixDef("adjacency", List.of(List.of(0)), null));
    assertThatThrownBy(() -> validator.validate(g))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("label");
  }

  @Test
  void emptyLabels_throws() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of(),
            new GraphContract.MatrixDef("adjacency", List.of(), null));
    assertThatThrownBy(() -> validator.validate(g))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("label");
  }

  // ── Dimension checks ──────────────────────────────────────────────────

  @Test
  void rowCountMismatch_throws() {
    GraphContract g =
        undirected(List.of("A", "B"), List.of(List.of(0, 1))); // only 1 row for 2 labels
    assertThatThrownBy(() -> validator.validate(g))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("row count");
  }

  @Test
  void columnCountMismatch_throws() {
    GraphContract g =
        undirected(List.of("A", "B"), List.of(List.of(0, 1), List.of(1))); // row 1 has only 1 col
    assertThatThrownBy(() -> validator.validate(g))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("columns");
  }

  // ── Symmetry check ────────────────────────────────────────────────────

  @Test
  void asymmetricUndirectedMatrix_throws() {
    GraphContract g =
        undirected(
            List.of("A", "B"), List.of(List.of(0, 1), List.of(0, 0))); // [0][1]=1 but [1][0]=0
    assertThatThrownBy(() -> validator.validate(g))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("Asymmetric");
  }

  @Test
  void asymmetricDirectedMatrix_passes() {
    GraphContract g =
        directed(
            List.of("A", "B"),
            List.of(List.of(0, 1), List.of(0, 0))); // directed: asymmetry is fine
    assertThatNoException().isThrownBy(() -> validator.validate(g));
  }

  // ── Incidence checks ──────────────────────────────────────────────────

  @Test
  void validUndirectedIncidence_passes() {
    // 3 nodes, 2 edges: edge0 = A-B, edge1 = B-C
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B", "C"),
            new GraphContract.MatrixDef(
                "incidence", List.of(List.of(1, 0), List.of(1, 1), List.of(0, 1)), null));
    assertThatNoException().isThrownBy(() -> validator.validate(g));
  }

  @Test
  void incidenceColumnNotSummingToTwo_throws() {
    GraphContract g =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B", "C"),
            new GraphContract.MatrixDef(
                "incidence",
                List.of(
                    List.of(1, 0),
                    List.of(0, 1), // column 0 sums to 1, not 2
                    List.of(0, 1)),
                null));
    assertThatThrownBy(() -> validator.validate(g))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("Incidence column");
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private GraphContract undirected(List<String> labels, List<List<Integer>> data) {
    return new GraphContract(
        "graph", null, false, false, labels, new GraphContract.MatrixDef("adjacency", data, null));
  }

  private GraphContract directed(List<String> labels, List<List<Integer>> data) {
    return new GraphContract(
        "graph", null, true, false, labels, new GraphContract.MatrixDef("adjacency", data, null));
  }
}
