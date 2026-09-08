package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.telemetry.service.StructureKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-21 · CA-1: el tipo que ve el docente. «graph» no sirve para decidir qué reforzar; «dirigido» o
 * «no dirigido» sí.
 */
class StructureKindTest {

  private static GraphContract graph(boolean directed) {
    return new GraphContract(
        "graph",
        null,
        directed,
        false,
        List.of("A", "B"),
        new GraphContract.MatrixDef("adjacency", List.of(List.of(0, 1), List.of(1, 0)), null));
  }

  private static TreeContract tree(String subtype) {
    return new TreeContract(
        "tree", null, subtype, List.of(new TreeContract.Operation("insert", List.of(1))), null);
  }

  @Test
  @DisplayName("el grafo distingue dirigido de no dirigido")
  void graphDirection() {
    assertThat(StructureKind.of(graph(false))).isEqualTo("grafo_no_dirigido");
    assertThat(StructureKind.of(graph(true))).isEqualTo("grafo_dirigido");
  }

  @Test
  @DisplayName("cada familia tiene su nombre en el vocabulario del syllabus")
  void everyFamily() {
    assertThat(StructureKind.of(tree("avl"))).isEqualTo("arbol_avl");
    assertThat(StructureKind.of(tree("bst"))).isEqualTo("arbol_bst");
    assertThat(StructureKind.of(tree("heap"))).isEqualTo("heap");
    assertThat(StructureKind.of(tree(null))).isEqualTo("arbol_binario");
    assertThat(StructureKind.of(new LinkedListContract("linked-list", null, "singly", List.of(1))))
        .isEqualTo("lista_simple");
    assertThat(StructureKind.of(new LinkedListContract("linked-list", null, "doubly", List.of(1))))
        .isEqualTo("lista_doble");
    assertThat(
            StructureKind.of(new LinkedListContract("linked-list", null, "circular", List.of(1))))
        .isEqualTo("lista_circular");
    assertThat(
            StructureKind.of(new HashTableContract("hash-table", null, 7, List.of(1), "modular")))
        .isEqualTo("tabla_hash");
    assertThat(StructureKind.of(new StackContract("stack", null, List.of(1)))).isEqualTo("pila");
    assertThat(StructureKind.of(new QueueContract("queue", null, List.of(1)))).isEqualTo("cola");
  }

  @Test
  @DisplayName("desde las cadenas de una petición de algoritmo, con la misma tabla")
  void fromStrings() {
    assertThat(StructureKind.of("tree", "avl")).isEqualTo("arbol_avl");
    assertThat(StructureKind.of("stack", "simple")).isEqualTo("pila");
    assertThat(StructureKind.of("QUEUE", null)).isEqualTo("cola");
    // Un grafo pedido por cadenas no dice su dirección: se registra el caso común.
    assertThat(StructureKind.of("graph", "simple")).isEqualTo("grafo_no_dirigido");
  }

  @Test
  @DisplayName("lo desconocido se nombra, no se inventa ni revienta")
  void unknown() {
    assertThat(StructureKind.of((com.vista.pdg.model.contract.def.StructureContract) null))
        .isEqualTo(StructureKind.DESCONOCIDA);
    assertThat(StructureKind.of("trie", null)).isEqualTo(StructureKind.DESCONOCIDA);
    assertThat(StructureKind.of((String) null, null)).isEqualTo(StructureKind.DESCONOCIDA);
  }
}
