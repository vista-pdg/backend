package com.vista.pdg.service.sdd.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.InvalidContractException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El modelo no es determinista; el normalizador sí. Cada prueba fija una variante que Gemini ha
 * producido o puede producir y el contrato estricto al que debe llegar.
 */
class ContractNormalizerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ContractNormalizer normalizer = new ContractNormalizer(mapper);

  private JsonNode norm(String json) throws Exception {
    return mapper.readTree(normalizer.normalize(json));
  }

  // ── Árboles ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("tree: 'values' sin 'operations' se convierte en una inserción en ese orden")
  void treeValuesBecomeInsertOperation() throws Exception {
    JsonNode n = norm("{\"type\":\"tree\",\"subtype\":\"bst\",\"values\":[1,2,3,5,6]}");
    assertThat(n.get("operations").get(0).get("op").asText()).isEqualTo("insert");
    assertThat(n.get("operations").get(0).get("values").toString()).isEqualTo("[1,2,3,5,6]");
  }

  @Test
  @DisplayName("tree: el tipo 'bst'/'avl'/'heap' a secas fija type=tree y el subtipo")
  void treeTypeAliases() throws Exception {
    assertThat(norm("{\"type\":\"bst\",\"values\":[2,1,3]}").get("subtype").asText())
        .isEqualTo("bst");
    assertThat(
            norm("{\"type\":\"AVL\",\"operations\":[{\"op\":\"insert\",\"values\":[1]}]}")
                .get("subtype")
                .asText())
        .isEqualTo("avl");
    JsonNode heap = norm("{\"type\":\"max-heap\",\"insertions\":[\"5\",\"3\",7]}");
    assertThat(heap.get("type").asText()).isEqualTo("tree");
    assertThat(heap.get("subtype").asText()).isEqualTo("heap");
    assertThat(heap.get("operations").get(0).get("values").toString()).isEqualTo("[5,3,7]");
    assertThat(norm("{\"type\":\"árbol binario\",\"values\":[1]}").get("subtype").asText())
        .isEqualTo("bst");
  }

  @Test
  @DisplayName("tree: nodos pre-construidos sin id, con valor en texto y padre por valor")
  void treePrebuiltNodesAreRepaired() throws Exception {
    JsonNode n =
        norm(
            "{\"type\":\"tree\",\"nodes\":[{\"value\":\"10\"},{\"value\":5,\"parent\":\"10\",\"side\":\"left\"},"
                + "{\"value\":15,\"parent\":\"10\",\"side\":\"right\"}]}");
    assertThat(n.get("subtype").asText()).isEqualTo("binary");
    assertThat(n.get("nodes").get(0).get("id").asText()).isEqualTo("n0");
    assertThat(n.get("nodes").get(0).get("value").asInt()).isEqualTo(10);
    assertThat(n.get("nodes").get(1).get("parent").asText()).isEqualTo("n0");
    assertThat(n.get("nodes").get(2).get("parent").asText()).isEqualTo("n0");
  }

  @Test
  @DisplayName("tree: 'op' ausente o con otro nombre, valores como texto")
  void treeOperationShapes() throws Exception {
    JsonNode n =
        norm(
            "{\"type\":\"tree\",\"subtype\":\"bst\",\"operations\":[{\"operation\":\"Insert\",\"values\":[\"8\",\"3\"]},{\"value\":10}]}");
    assertThat(n.get("operations").get(0).get("op").asText()).isEqualTo("insert");
    assertThat(n.get("operations").get(0).get("values").toString()).isEqualTo("[8,3]");
    assertThat(n.get("operations").get(1).get("values").toString()).isEqualTo("[10]");
  }

  // ── Grafos ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("graph: lista de aristas sin matriz → matriz de adyacencia")
  void graphEdgesBecomeMatrix() throws Exception {
    JsonNode n =
        norm(
            "{\"type\":\"graph\",\"directed\":true,\"labels\":[\"A\",\"B\",\"C\"],"
                + "\"edges\":[{\"from\":\"A\",\"to\":\"B\",\"weight\":3},{\"source\":\"B\",\"target\":\"C\"}]}");
    assertThat(n.get("matrix").get("kind").asText()).isEqualTo("adjacency");
    assertThat(n.get("matrix").get("data").toString()).isEqualTo("[[0,3,0],[0,0,1],[0,0,0]]");
    assertThat(n.get("weighted").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("graph: nodos como objetos, matriz directa con booleanos, sin directed/weighted")
  void graphLooseShapes() throws Exception {
    JsonNode n =
        norm(
            "{\"type\":\"grafo\",\"nodes\":[{\"id\":\"x\"},{\"label\":\"y\"}],"
                + "\"matrix\":[[false,true],[true,false]]}");
    assertThat(n.get("type").asText()).isEqualTo("graph");
    assertThat(n.get("labels").toString()).isEqualTo("[\"x\",\"y\"]");
    assertThat(n.get("directed").asBoolean()).isFalse();
    assertThat(n.get("weighted").asBoolean()).isFalse();
    assertThat(n.get("matrix").get("data").toString()).isEqualTo("[[0,1],[1,0]]");
  }

  @Test
  @DisplayName("graph: matriz sin etiquetas → A, B, C…; 'adjacencyMatrix' como nombre alterno")
  void graphMatrixWithoutLabels() throws Exception {
    JsonNode n = norm("{\"type\":\"graph\",\"adjacencyMatrix\":[[0,1,1],[1,0,1],[1,1,0]]}");
    assertThat(n.get("labels").toString()).isEqualTo("[\"A\",\"B\",\"C\"]");
    assertThat(n.get("matrix").get("data").size()).isEqualTo(3);
  }

  @Test
  @DisplayName("graph: la matriz de incidencia se reconoce por su forma n×m")
  void graphIncidenceDetected() throws Exception {
    JsonNode n =
        norm(
            "{\"type\":\"graph\",\"labels\":[\"A\",\"B\",\"C\"],\"matrix\":{\"data\":[[1,0],[1,1],[0,1]]}}");
    assertThat(n.get("matrix").get("kind").asText()).isEqualTo("incidence");
  }

  // ── Listas, tablas, pilas, colas ───────────────────────────────────────

  @Test
  @DisplayName("linked-list: alias de tipo y subtipo, valores en 'elements'")
  void linkedListAliases() throws Exception {
    JsonNode n =
        norm("{\"type\":\"lista enlazada\",\"subtype\":\"doble\",\"elements\":[\"10\",20]}");
    assertThat(n.get("type").asText()).isEqualTo("linked-list");
    assertThat(n.get("subtype").asText()).isEqualTo("doubly");
    assertThat(n.get("values").toString()).isEqualTo("[10,20]");
    assertThat(norm("{\"type\":\"singly-linked-list\",\"values\":[1]}").get("subtype").asText())
        .isEqualTo("singly");
  }

  @Test
  @DisplayName("hash-table: 'buckets' como tamaño, función por defecto")
  void hashTableAliases() throws Exception {
    JsonNode n = norm("{\"type\":\"hashtable\",\"buckets\":\"7\",\"keys\":[15,22,35]}");
    assertThat(n.get("type").asText()).isEqualTo("hash-table");
    assertThat(n.get("size").asInt()).isEqualTo(7);
    assertThat(n.get("values").toString()).isEqualTo("[15,22,35]");
    assertThat(n.get("hashFunction").asText()).isEqualTo("modular");
  }

  @Test
  @DisplayName("stack y queue: alias en español y valores como objetos")
  void stackAndQueueAliases() throws Exception {
    JsonNode s = norm("{\"type\":\"pila\",\"items\":[{\"value\":3},{\"value\":\"42\"}]}");
    assertThat(s.get("type").asText()).isEqualTo("stack");
    assertThat(s.get("values").toString()).isEqualTo("[3,42]");
    JsonNode q = norm("{\"type\":\"Cola\",\"values\":[5,9]}");
    assertThat(q.get("type").asText()).isEqualTo("queue");
  }

  // ── Envoltorios y errores ──────────────────────────────────────────────

  @Test
  @DisplayName("una respuesta envuelta en {\"contract\": …} se desenvuelve")
  void wrappedContractIsUnwrapped() throws Exception {
    JsonNode n = norm("{\"contract\":{\"type\":\"stack\",\"values\":[1,2]}}");
    assertThat(n.get("type").asText()).isEqualTo("stack");
  }

  @Test
  @DisplayName("sin 'type', sin objeto o con JSON roto se rechaza con InvalidContractException")
  void invalidInputs() {
    assertThatThrownBy(() -> normalizer.normalize("{\"values\":[1]}"))
        .isInstanceOf(InvalidContractException.class);
    assertThatThrownBy(() -> normalizer.normalize("[1,2]"))
        .isInstanceOf(InvalidContractException.class);
    assertThatThrownBy(() -> normalizer.normalize("{not json"))
        .isInstanceOf(InvalidContractException.class);
  }

  @Test
  @DisplayName("un contrato ya estricto sale igual")
  void strictContractIsUntouched() throws Exception {
    String strict =
        "{\"type\":\"tree\",\"subtype\":\"avl\",\"operations\":[{\"op\":\"insert\",\"values\":[10,5,15]}]}";
    assertThat(norm(strict)).isEqualTo(mapper.readTree(strict));
  }
}
