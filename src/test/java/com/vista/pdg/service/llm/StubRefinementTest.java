package com.vista.pdg.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.llm.def.ConversationContext;
import com.vista.pdg.service.llm.impl.StubLlmAdapter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El sustituto del modelo en el perfil {@code e2e} debe refinar de forma determinista (HU-32): sin
 * esto, los escenarios de refinamiento no podrían probarse de extremo a extremo sin llamar a
 * Gemini.
 */
class StubRefinementTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final StubLlmAdapter stub = new StubLlmAdapter(mapper);

  private ConversationContext ctx(StructureContract contract) throws Exception {
    return new ConversationContext(mapper.writeValueAsString(contract), List.of());
  }

  @Test
  @DisplayName("HU-21 · CA-3: lo que no está en el syllabus se rechaza en vez de inventarse")
  void outOfSyllabusIsRejected() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> stub.generate("un arbol rojinegro"))
        .isInstanceOf(com.vista.pdg.exception.UnsupportedStructureException.class)
        .hasMessageContaining("syllabus");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> stub.generate("una trie de prefijos"))
        .isInstanceOf(com.vista.pdg.exception.UnsupportedStructureException.class);
  }

  @Test
  @DisplayName("sin contexto genera la estructura descrita, incluidos árboles")
  void generatesFromScratch() {
    StructureContract tree = stub.generate("Genera un arbol con insercion de 1, 2, 3, 5, 6");
    assertThat(tree).isInstanceOf(TreeContract.class);
    assertThat(((TreeContract) tree).subtype()).isEqualTo("bst");
    assertThat(((TreeContract) tree).operations().getFirst().values())
        .containsExactly(1, 2, 3, 5, 6);
    assertThat(((TreeContract) stub.generate("AVL con 10, 5, 15")).subtype()).isEqualTo("avl");
    assertThat(stub.generate("grafo de 5 nodos")).isInstanceOf(GraphContract.class);
  }

  @Test
  @DisplayName(
      "CA-1: «ahora inserta el 7» añade el valor al árbol vigente sin perder los anteriores")
  void refinesTreeInsertion() throws Exception {
    TreeContract before =
        new TreeContract(
            "tree",
            null,
            "bst",
            List.of(new TreeContract.Operation("insert", List.of(1, 2, 3, 5, 6))),
            null);

    StructureContract after = stub.generate("ahora inserta el 7", ctx(before));

    assertThat(after).isInstanceOf(TreeContract.class);
    assertThat(((TreeContract) after).operations().getFirst().values())
        .containsExactly(1, 2, 3, 5, 6, 7);
    assertThat(((TreeContract) after).subtype()).isEqualTo("bst");
  }

  @Test
  @DisplayName("CA-2: «hazlo dirigido» conserva etiquetas y aristas, una sola dirección por par")
  void refinesGraphDirection() throws Exception {
    GraphContract cycle =
        new GraphContract(
            "graph",
            null,
            false,
            false,
            List.of("A", "B", "C", "D"),
            new GraphContract.MatrixDef(
                "adjacency",
                List.of(
                    List.of(0, 1, 0, 1),
                    List.of(1, 0, 1, 0),
                    List.of(0, 1, 0, 1),
                    List.of(1, 0, 1, 0)),
                null));

    StructureContract after = stub.generate("hazlo dirigido", ctx(cycle));

    assertThat(after).isInstanceOf(GraphContract.class);
    GraphContract g = (GraphContract) after;
    assertThat(g.directed()).isTrue();
    assertThat(g.labels()).containsExactly("A", "B", "C", "D");
    int ones = g.matrix().data().stream().flatMap(List::stream).mapToInt(Integer::intValue).sum();
    assertThat(ones).as("cuatro aristas, ahora en un solo sentido").isEqualTo(4);
  }

  @Test
  @DisplayName("CA-3: nombrar otra familia empieza de cero aunque haya sesión")
  void newStructureIgnoresContext() throws Exception {
    TreeContract before =
        new TreeContract(
            "tree", null, "bst", List.of(new TreeContract.Operation("insert", List.of(10))), null);

    StructureContract after = stub.generate("ahora una pila con 3, 42, 8", ctx(before));

    assertThat(after).isInstanceOf(StackContract.class);
    assertThat(((StackContract) after).values()).containsExactly(3, 42, 8);
  }

  @Test
  @DisplayName("una pila vigente también admite inserciones; un contexto ilegible no rompe nada")
  void refinesStackAndSurvivesGarbage() throws Exception {
    StackContract stack = new StackContract("stack", null, List.of(3, 42));
    StructureContract after = stub.generate("agrega 8", ctx(stack));
    assertThat(((StackContract) after).values()).containsExactly(3, 42, 8);

    StructureContract fallback =
        stub.generate("inserta 9", new ConversationContext("{no json", List.of()));
    assertThat(fallback).isInstanceOf(GraphContract.class);
  }
}
