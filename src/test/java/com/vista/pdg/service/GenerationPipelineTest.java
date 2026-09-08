package com.vista.pdg.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Generación por tipo de estructura, sin modelo: el JSON de cada caso es lo que el modelo responde
 * (en su forma estricta y en las laxas que el normalizador acepta) y se lleva por toda la cadena
 * —normalizar → validar → generar → disponer— hasta la respuesta que recibe el frontend.
 *
 * <p>Lo que se afirma es lo que el estudiante ve: el tipo pedido, el número de nodos que pidió y
 * que <b>ningún par de nodos comparte posición</b>. Esto último es determinista y es lo que la
 * llamada real al modelo no puede garantizar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "gemini.api.key=test-key-no-usada",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    })
class GenerationPipelineTest {

  @Autowired private ContractBuilder builder;
  @Autowired private GeneratorDispatcher generators;
  @Autowired private LayoutDispatcher layouts;

  private StructureResponse run(String json) {
    var contract = builder.build(json);
    GeneratedStructure structure = generators.dispatch(contract);
    Map<String, Vec3> positions = layouts.compute(structure);
    return StructureResponse.of(contract, structure, positions);
  }

  private static void assertAllApart(StructureResponse r, double minDistance) {
    List<Node3D> nodes = r.nodes();
    for (int i = 0; i < nodes.size(); i++) {
      for (int j = i + 1; j < nodes.size(); j++) {
        Node3D a = nodes.get(i), b = nodes.get(j);
        double d =
            Math.sqrt(
                Math.pow(a.x() - b.x(), 2)
                    + Math.pow(a.y() - b.y(), 2)
                    + Math.pow(a.z() - b.z(), 2));
        assertThat(d)
            .as("%s y %s no deben superponerse", a.label(), b.label())
            .isGreaterThanOrEqualTo(minDistance);
        assertThat(Double.isFinite(a.x()) && Double.isFinite(a.y()) && Double.isFinite(a.z()))
            .isTrue();
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "árbol del usuario (values sin operations) | {\"type\":\"tree\",\"subtype\":\"bst\",\"values\":[1,2,3,5,6]} | tree | 5",
        "BST estricto                             | {\"type\":\"tree\",\"subtype\":\"bst\",\"operations\":[{\"op\":\"insert\",\"values\":[8,3,10,1,6,14,4,7]}]} | tree | 8",
        "AVL con rotaciones                        | {\"type\":\"tree\",\"subtype\":\"avl\",\"operations\":[{\"op\":\"insert\",\"values\":[1,2,3,4,5,6,7]}]} | tree | 7",
        "heap                                     | {\"type\":\"max-heap\",\"values\":[5,3,7,1,9,2]} | tree | 6",
        "árbol con nodos pre-construidos          | {\"type\":\"tree\",\"nodes\":[{\"value\":10},{\"value\":5,\"parent\":\"10\"},{\"value\":15,\"parent\":\"10\"},{\"value\":7,\"parent\":\"5\"}]} | tree | 4",
        "grafo K4 con matriz                       | {\"type\":\"graph\",\"directed\":false,\"weighted\":false,\"labels\":[\"A\",\"B\",\"C\",\"D\"],\"matrix\":{\"kind\":\"adjacency\",\"data\":[[0,1,1,1],[1,0,1,1],[1,1,0,1],[1,1,1,0]]}} | graph | 4",
        "grafo dirigido con lista de aristas       | {\"type\":\"graph\",\"directed\":true,\"labels\":[\"A\",\"B\",\"C\",\"D\",\"E\"],\"edges\":[{\"from\":\"A\",\"to\":\"B\"},{\"from\":\"B\",\"to\":\"C\"},{\"from\":\"C\",\"to\":\"D\"},{\"from\":\"D\",\"to\":\"E\"},{\"from\":\"E\",\"to\":\"A\"}]} | graph | 5",
        "grafo de 12 nodos                         | {\"type\":\"graph\",\"adjacencyMatrix\":[[0,1,0,0,0,0,0,0,0,0,0,1],[1,0,1,0,0,0,0,0,0,0,0,0],[0,1,0,1,0,0,0,0,0,0,0,0],[0,0,1,0,1,0,0,0,0,0,0,0],[0,0,0,1,0,1,0,0,0,0,0,0],[0,0,0,0,1,0,1,0,0,0,0,0],[0,0,0,0,0,1,0,1,0,0,0,0],[0,0,0,0,0,0,1,0,1,0,0,0],[0,0,0,0,0,0,0,1,0,1,0,0],[0,0,0,0,0,0,0,0,1,0,1,0],[0,0,0,0,0,0,0,0,0,1,0,1],[1,0,0,0,0,0,0,0,0,0,1,0]]} | graph | 12",
        "lista simple                              | {\"type\":\"linked-list\",\"subtype\":\"singly\",\"values\":[1,2,3,4,5]} | linked-list | 5",
        "lista circular con alias                  | {\"type\":\"lista\",\"subtype\":\"circular\",\"elements\":[7,14,21,28]} | linked-list | 4",
        "tabla hash                                | {\"type\":\"hash-table\",\"size\":7,\"values\":[15,22,35,8,43,10],\"hashFunction\":\"modular\"} | hash-table | 13",
        "pila                                      | {\"type\":\"pila\",\"values\":[3,42,8,17]} | stack | 4",
        "cola                                      | {\"type\":\"queue\",\"items\":[5,9,1,14]} | queue | 4",
      })
  @DisplayName("cada tipo genera lo pedido con todos los nodos separados")
  void everyTypeRendersApart(String name, String json, String type, int nodes) {
    StructureResponse r = run(json);
    assertThat(r.error()).as(name).isFalse();
    assertThat(r.meta().type()).isEqualTo(type);
    assertThat(r.nodes()).hasSize(nodes);
    assertAllApart(r, 0.9);
  }

  @Test
  @DisplayName(
      "el árbol del usuario: inserción de 1, 2, 3, 5, 6 en un BST degenera en escalera y se ve completa")
  void degenerateBstIsFullyVisible() {
    StructureResponse r = run("{\"type\":\"tree\",\"values\":[1,2,3,5,6]}");
    assertThat(r.nodes()).extracting(Node3D::label).containsExactly("1", "2", "3", "5", "6");
    assertThat(r.nodes()).extracting(Node3D::depth).containsExactly(0, 1, 2, 3, 4);
    assertThat(r.edges()).hasSize(4);
    assertAllApart(r, 1.5);
  }

  @Test
  @DisplayName("un bosque (varios nodos sin padre) no apila los árboles en el origen")
  void forestIsSpread() {
    StructureResponse r =
        run(
            "{\"type\":\"tree\",\"nodes\":[{\"id\":\"a\",\"value\":1},{\"id\":\"b\",\"value\":2,\"parent\":\"a\"},"
                + "{\"id\":\"c\",\"value\":3},{\"id\":\"d\",\"value\":4,\"parent\":\"c\"},{\"id\":\"e\",\"value\":5,\"parent\":\"zz\"}]}");
    assertThat(r.nodes()).hasSize(5);
    assertAllApart(r, 1.5);
  }

  @Test
  @DisplayName("una pista de layout desconocida no rompe: manda el tipo de estructura")
  void unknownLayoutHintFallsBackToType() {
    StructureResponse r =
        run(
            "{\"type\":\"tree\",\"subtype\":\"bst\",\"visual\":{\"layout\":\"hierarchical\"},\"operations\":[{\"op\":\"insert\",\"values\":[2,1,3]}]}");
    assertThat(r.nodes()).hasSize(3);
    // hierarchical3d: la raíz arriba y los hijos un nivel más abajo.
    Node3D root = r.nodes().stream().filter(n -> n.parent() == null).findFirst().orElseThrow();
    assertThat(r.nodes().stream().filter(n -> n.parent() != null)).allMatch(n -> n.y() < root.y());
    assertAllApart(r, 1.5);
  }
}
