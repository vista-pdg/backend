package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import com.vista.pdg.service.algorithm.impl.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GraphAlgorithmsTest {
  private final List<AlgorithmStrategy> algorithms =
      List.of(
          new GraphDfsAlgorithm(),
          new GraphDijkstraAlgorithm(),
          new GraphFloydAlgorithm(),
          new GraphPrimAlgorithm(),
          new GraphKruskalAlgorithm());

  private static Node3D node(String id) {
    return new Node3D(id, id.toUpperCase(), 1, 2, 3, 0, null, Map.of("original", true));
  }

  private static Edge3D edge(String a, String b, Integer w, boolean directed) {
    return new Edge3D(a + b + w, a, b, w, directed);
  }

  private AlgorithmRequest request(String op, List<Edge3D> edges, String start) {
    return new AlgorithmRequest(
        "graph",
        "simple",
        op,
        null,
        List.of(node("a"), node("b"), node("c"), node("d")),
        edges,
        start);
  }

  private List<Edge3D> weighted() {
    return List.of(
        edge("a", "b", 4, false),
        edge("a", "c", 1, false),
        edge("c", "b", 2, false),
        edge("b", "d", 1, false),
        edge("c", "d", 8, false));
  }

  private AlgorithmStep last(StepsResponse response) {
    assertThat(response.error()).as(response.message()).isFalse();
    return response.steps().getLast();
  }

  @Test
  void catalogIncludesAllSixGraphAlgorithms() {
    var all = new ArrayList<>(algorithms);
    all.add(new GraphBfsAlgorithm());
    assertThat(new AlgorithmDispatcher(all).catalog())
        .extracting(d -> d.operation())
        .containsExactly("bfs", "dfs", "dijkstra", "floyd", "kruskal", "prim");
  }

  @Test
  void dfsExploresDepthFirstAndRespectsDirection() {
    var dfs = new GraphDfsAlgorithm();
    var r =
        dfs.generate(
            request(
                "dfs",
                List.of(
                    edge("a", "b", null, true),
                    edge("a", "c", null, true),
                    edge("b", "d", null, true)),
                "a"));
    assertThat(last(r).highlightedNodeIds()).containsExactly("a", "b", "d", "c");
    assertThat(
            last(dfs.generate(request("dfs", List.of(edge("a", "b", null, true)), "b")))
                .highlightedNodeIds())
        .containsExactly("b");
  }

  @Test
  void dijkstraRelaxesDistancesRatherThanUsingBfsOrder() {
    var r = new GraphDijkstraAlgorithm().generate(request("dijkstra", weighted(), "a"));
    assertThat(last(r).variables().get("distancias")).isEqualTo("A: 0, B: 3, C: 1, D: 4");
    assertThat(last(r).variables().get("anteriores")).contains("b=c", "d=b");
    assertThat(r.steps().getFirst().variables().get("distancias"))
        .isEqualTo("A: 0, B: ∞, C: ∞, D: ∞");
  }

  @Test
  void dijkstraHandlesZeroMissingParallelWeightsAndUnreachableNodes() {
    var edges =
        List.of(edge("a", "b", 9, true), edge("a", "b", 0, true), edge("b", "c", null, true));
    var end = last(new GraphDijkstraAlgorithm().generate(request("dijkstra", edges, "a")));
    assertThat(end.variables().get("distancias")).isEqualTo("A: 0, B: 0, C: 1, D: ∞");
    assertThat(end.description()).contains("No alcanzables", "D");
    assertThat(
            new GraphDijkstraAlgorithm()
                .generate(request("dijkstra", List.of(edge("a", "b", -1, true)), "a"))
                .error())
        .isTrue();
  }

  @Test
  void floydSupportsNegativeEdgesAndEveryOrigin() {
    var r =
        new GraphFloydAlgorithm()
            .generate(
                request(
                    "floyd",
                    List.of(
                        edge("a", "b", 4, true),
                        edge("a", "c", 10, true),
                        edge("b", "c", -2, true),
                        edge("c", "d", 1, true)),
                    null));
    assertThat(last(r).variables())
        .containsEntry("D[a] · A", "[0, 4, 2, 3]")
        .containsEntry("D[b] · B", "[∞, 0, -2, -1]")
        .containsEntry("D[d] · D", "[∞, ∞, ∞, 0]");
    assertThat(r.steps().getFirst().variables().get("D[a] · A")).isEqualTo("[0, 4, 10, ∞]");
  }

  @Test
  void floydRejectsNegativeCyclesIncludingDisconnectedAndSelfLoops() {
    var floyd = new GraphFloydAlgorithm();
    for (var edges :
        List.of(
            List.of(edge("a", "a", -1, true)),
            List.of(edge("c", "d", -2, true), edge("d", "c", 1, true)),
            List.of(edge("a", "b", -1, false)))) {
      assertThat(floyd.generate(request("floyd", edges, null)).message())
          .contains("ciclo negativo");
    }
  }

  @Test
  void spanningTreesAgreeOnCostAndHandleDisconnectedGraphs() {
    for (AlgorithmStrategy a : List.of(new GraphPrimAlgorithm(), new GraphKruskalAlgorithm())) {
      assertThat(last(a.generate(request(a.descriptor().operation(), weighted(), "a"))).variables())
          .containsEntry("costo", "4")
          .containsEntry("componentes", "1");
      var edges =
          List.of(edge("a", "b", 4, false), edge("a", "b", -2, false), edge("b", "b", -99, false));
      var end = last(a.generate(request(a.descriptor().operation(), edges, "c")));
      assertThat(end.variables()).containsEntry("costo", "-2").containsEntry("componentes", "3");
      assertThat(end.variables().get("aristas")).isEqualTo("[A–B (-2)]");
      assertThat(
              a.generate(request(a.descriptor().operation(), List.of(edge("a", "b", 1, true)), "a"))
                  .message())
          .contains("no dirigido");
    }
  }

  @Test
  void sumsDoNotOverflowIntegerWeights() {
    var edges =
        List.of(edge("a", "b", Integer.MAX_VALUE, true), edge("b", "c", Integer.MAX_VALUE, true));
    assertThat(
            last(new GraphDijkstraAlgorithm().generate(request("dijkstra", edges, "a")))
                .variables()
                .get("distancias"))
        .contains("C: 4294967294");
    assertThat(
            last(new GraphFloydAlgorithm().generate(request("floyd", edges, null)))
                .variables()
                .get("D[a] · A"))
        .contains("4294967294");
  }

  @Test
  void tracesAreDeterministicImmutableAndInstrumented() {
    for (AlgorithmStrategy a : algorithms) {
      var req = request(a.descriptor().operation(), weighted(), "a");
      var r = a.generate(req);
      assertThat(r).isEqualTo(a.generate(req));
      for (int i = 0; i < r.steps().size(); i++) {
        var step = r.steps().get(i);
        assertThat(step.index()).isEqualTo(i);
        assertThat(step.line()).isBetween(1, r.code().size());
        assertThat(step.variables()).isNotEmpty();
        assertThat(step.nodes())
            .hasSize(4)
            .allSatisfy(
                n -> {
                  assertThat(n.x()).isEqualTo(1);
                  assertThat(n.properties()).containsEntry("original", true).containsKey("state");
                });
        assertThat(step.edges()).isEqualTo(req.edges());
      }
      assertThat(req.nodes())
          .allSatisfy(n -> assertThat(n.properties()).doesNotContainKey("state"));
      assertThat(
              a.generate(new AlgorithmRequest("graph", "simple", a.descriptor().operation(), null))
                  .error())
          .isTrue();
      assertThat(
              a.generate(
                      request(
                          a.descriptor().operation(), List.of(edge("a", "missing", 1, false)), "a"))
                  .error())
          .isTrue();
    }
  }
}
