package com.vista.pdg.service.sdd.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vista.pdg.exception.InvalidContractException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Normaliza el JSON que devuelve el modelo antes de convertirlo en un contrato.
 *
 * <p>La salida del modelo no es determinista: la misma instrucción puede llegar con {@code "type":
 * "bst"} en vez de {@code "tree"} + {@code "subtype": "bst"}, con {@code "values"} en lugar de
 * {@code "operations"}, con la lista de aristas en vez de la matriz, o con los números entre
 * comillas. En lugar de rechazar cada variante y volver a llamar al modelo (lento, caro y otra vez
 * no determinista), este paso <b>determinista y probado</b> traduce las formas laxas más comunes al
 * contrato estricto. Lo que no puede traducirse sigue llegando al validador, que lo rechaza con un
 * mensaje que el siguiente intento del modelo recibe como corrección.
 */
@Component
public class ContractNormalizer {

  private static final Map<String, String> TYPE_ALIASES = new HashMap<>();
  private static final Map<String, String> TREE_SUBTYPES = new HashMap<>();
  private static final Map<String, String> LIST_SUBTYPES = new HashMap<>();

  static {
    for (String s : List.of("graph", "grafo", "digraph", "grafo dirigido", "network", "red"))
      TYPE_ALIASES.put(s, "graph");
    for (String s :
        List.of(
            "tree",
            "arbol",
            "árbol",
            "binary-tree",
            "binary tree",
            "binarytree",
            "arbol binario",
            "árbol binario",
            "bst",
            "avl",
            "heap",
            "binary-search-tree",
            "max-heap",
            "min-heap",
            "monticulo",
            "montículo")) TYPE_ALIASES.put(s, "tree");
    for (String s :
        List.of(
            "linked-list",
            "linked_list",
            "linkedlist",
            "linked list",
            "list",
            "lista",
            "lista enlazada",
            "lista-enlazada",
            "singly-linked-list",
            "doubly-linked-list")) TYPE_ALIASES.put(s, "linked-list");
    for (String s :
        List.of(
            "hash-table",
            "hash_table",
            "hashtable",
            "hash table",
            "hash",
            "tabla hash",
            "tabla-hash",
            "hashmap",
            "map",
            "diccionario")) TYPE_ALIASES.put(s, "hash-table");
    for (String s : List.of("stack", "pila", "lifo")) TYPE_ALIASES.put(s, "stack");
    for (String s : List.of("queue", "cola", "fifo")) TYPE_ALIASES.put(s, "queue");

    for (String s : List.of("bst", "binary-search-tree", "binary search tree", "search", "binary"))
      TREE_SUBTYPES.put(s, "bst");
    for (String s : List.of("avl", "avl-tree", "avl tree")) TREE_SUBTYPES.put(s, "avl");
    for (String s :
        List.of("heap", "max-heap", "min-heap", "maxheap", "minheap", "monticulo", "montículo"))
      TREE_SUBTYPES.put(s, "heap");

    for (String s : List.of("singly", "single", "simple", "simply", "sencilla", "simple-linked"))
      LIST_SUBTYPES.put(s, "singly");
    for (String s : List.of("doubly", "double", "doble", "doblemente"))
      LIST_SUBTYPES.put(s, "doubly");
    for (String s : List.of("circular", "circle", "ring")) LIST_SUBTYPES.put(s, "circular");
  }

  private final ObjectMapper mapper;

  public ContractNormalizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Devuelve el JSON normalizado como texto; el resto de la cadena no cambia. */
  public String normalize(String json) {
    JsonNode tree;
    try {
      tree = mapper.readTree(json);
    } catch (Exception e) {
      throw new InvalidContractException("Failed to parse contract JSON: " + e.getMessage());
    }
    if (tree == null || !tree.isObject()) {
      throw new InvalidContractException("Contract must be a JSON object");
    }
    ObjectNode root = normalizeObject((ObjectNode) tree);
    try {
      return mapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new InvalidContractException(
          "Failed to serialize normalized contract: " + e.getMessage());
    }
  }

  ObjectNode normalizeObject(ObjectNode root) {
    // Algunos modelos envuelven la respuesta: {"contract": {...}} o {"structure": {...}}.
    for (String wrapper : List.of("contract", "structure", "result", "data")) {
      if (!root.has("type") && root.get(wrapper) != null && root.get(wrapper).isObject()) {
        root = (ObjectNode) root.get(wrapper);
      }
    }
    String rawType = text(root.get("type"));
    if (rawType == null && root.has("structureType")) rawType = text(root.get("structureType"));
    if (rawType == null) throw new InvalidContractException("Contract is missing the 'type' field");
    String key = rawType.trim().toLowerCase(Locale.ROOT);
    String type = TYPE_ALIASES.getOrDefault(key, key);
    root.put("type", type);

    switch (type) {
      case "tree" -> normalizeTree(root, key);
      case "graph" -> normalizeGraph(root);
      case "linked-list" -> normalizeLinkedList(root, key);
      case "hash-table" -> normalizeHashTable(root);
      case "stack", "queue" -> normalizeSequence(root);
      default -> {
        /* el validador lo rechazará como tipo no soportado */
      }
    }
    return root;
  }

  // ── Árboles ──────────────────────────────────────────────────────────────

  private void normalizeTree(ObjectNode root, String rawTypeKey) {
    String subtype = text(root.get("subtype"));
    if (subtype == null) subtype = TREE_SUBTYPES.get(rawTypeKey);
    if (subtype == null && root.has("kind")) subtype = text(root.get("kind"));
    if (subtype != null) {
      String k = subtype.trim().toLowerCase(Locale.ROOT);
      subtype = TREE_SUBTYPES.getOrDefault(k, k);
    }
    boolean hasNodes =
        root.get("nodes") != null && root.get("nodes").isArray() && !root.get("nodes").isEmpty();
    boolean hasOps =
        root.get("operations") != null
            && root.get("operations").isArray()
            && !root.get("operations").isEmpty();

    // «values» / «insert» / «insertions» sin «operations»: una sola inserción en ese orden.
    if (!hasOps && !hasNodes) {
      ArrayNode values = null;
      for (String f : List.of("values", "insert", "insertions", "elements", "keys", "items")) {
        if (root.get(f) != null && root.get(f).isArray()) {
          values = intArray((ArrayNode) root.get(f));
          break;
        }
      }
      if (values != null && !values.isEmpty()) {
        ArrayNode ops = mapper.createArrayNode();
        ObjectNode op = mapper.createObjectNode();
        op.put("op", "insert");
        op.set("values", values);
        ops.add(op);
        root.set("operations", ops);
        hasOps = true;
      }
    }
    if (hasOps) {
      for (JsonNode op : root.get("operations")) {
        if (!op.isObject()) continue;
        ObjectNode o = (ObjectNode) op;
        String opName = text(o.get("op"));
        if (opName == null) opName = text(o.get("operation"));
        if (opName == null) opName = text(o.get("type"));
        o.put("op", opName == null ? "insert" : opName.trim().toLowerCase(Locale.ROOT));
        if (o.get("values") != null && o.get("values").isArray())
          o.set("values", intArray((ArrayNode) o.get("values")));
        else if (o.get("value") != null && o.get("value").isNumber()) {
          ArrayNode one = mapper.createArrayNode();
          one.add(o.get("value").asInt());
          o.set("values", one);
        }
      }
    }
    if (hasNodes) normalizeTreeNodes((ArrayNode) root.get("nodes"));
    if (subtype == null) subtype = hasNodes && !hasOps ? "binary" : "bst";
    root.put("subtype", subtype);
  }

  /**
   * Los nodos pre-construidos llegan a veces sin id (se numeran), con el valor como texto o con el
   * padre referido por su valor en vez de por su id; se resuelve cuando el valor es único.
   */
  private void normalizeTreeNodes(ArrayNode nodes) {
    Map<String, String> idByValue = new LinkedHashMap<>();
    List<String> duplicated = new ArrayList<>();
    int i = 0;
    for (JsonNode n : nodes) {
      if (!n.isObject()) continue;
      ObjectNode o = (ObjectNode) n;
      if (text(o.get("id")) == null) o.put("id", "n" + i);
      if (o.get("value") != null && o.get("value").isTextual()) {
        Integer v = parseInt(o.get("value").asText());
        if (v != null) o.put("value", v);
      }
      if (o.get("value") == null && o.get("label") != null) {
        Integer v = parseInt(o.get("label").asText());
        if (v != null) o.put("value", v);
      }
      String value = o.get("value") == null ? null : o.get("value").asText();
      if (value != null) {
        if (idByValue.containsKey(value)) duplicated.add(value);
        else idByValue.put(value, text(o.get("id")));
      }
      i++;
    }
    java.util.Set<String> ids = new java.util.HashSet<>();
    for (JsonNode n : nodes) if (n.isObject()) ids.add(text(n.get("id")));
    for (JsonNode n : nodes) {
      if (!n.isObject()) continue;
      ObjectNode o = (ObjectNode) n;
      JsonNode parent = o.get("parent");
      if (parent == null || parent.isNull()) {
        if (o.has("parentId")) o.set("parent", o.get("parentId"));
        continue;
      }
      String p = parent.asText();
      if (!ids.contains(p) && idByValue.containsKey(p) && !duplicated.contains(p)) {
        o.put("parent", idByValue.get(p));
      }
    }
  }

  // ── Grafos ───────────────────────────────────────────────────────────────

  private void normalizeGraph(ObjectNode root) {
    for (String f : List.of("directed", "weighted")) {
      JsonNode v = root.get(f);
      if (v == null || v.isNull()) root.put(f, false);
      else if (v.isTextual()) root.put(f, Boolean.parseBoolean(v.asText().trim()));
    }
    // Etiquetas: «labels» o «nodes»/«vertices» (cadenas u objetos con id/label).
    if (!(root.get("labels") != null && root.get("labels").isArray())) {
      for (String f : List.of("nodes", "vertices", "vertexes")) {
        if (root.get(f) != null && root.get(f).isArray()) {
          ArrayNode labels = mapper.createArrayNode();
          for (JsonNode n : root.get(f)) {
            if (n.isObject()) {
              String l = text(n.get("label"));
              if (l == null) l = text(n.get("id"));
              if (l == null) l = text(n.get("name"));
              labels.add(l == null ? String.valueOf(labels.size()) : l);
            } else labels.add(n.asText());
          }
          root.set("labels", labels);
          break;
        }
      }
    }
    // Matriz como array directo ({"matrix": [[...]]}) o con nombres alternos.
    JsonNode matrix = root.get("matrix");
    if (matrix == null) {
      for (String f : List.of("adjacency", "adjacencyMatrix", "adjacency_matrix")) {
        if (root.get(f) != null) {
          matrix = root.get(f);
          break;
        }
      }
    }
    if (matrix != null && matrix.isArray()) {
      ObjectNode m = mapper.createObjectNode();
      m.put("kind", "adjacency");
      m.set("data", boolToInt((ArrayNode) matrix));
      root.set("matrix", m);
      matrix = m;
    } else if (matrix != null && matrix.isObject()) {
      ObjectNode m = (ObjectNode) matrix;
      if (text(m.get("kind")) == null)
        m.put("kind", m.has("data") && isIncidenceShaped(root, m) ? "incidence" : "adjacency");
      if (m.get("data") == null) {
        for (String f : List.of("values", "rows", "matrix")) {
          if (m.get(f) != null && m.get(f).isArray()) {
            m.set("data", m.get(f));
            break;
          }
        }
      }
      if (m.get("data") != null && m.get("data").isArray())
        m.set("data", boolToInt((ArrayNode) m.get("data")));
      root.set("matrix", m);
    }
    // Sin matriz pero con lista de aristas: se construye la de adyacencia.
    if (root.get("matrix") == null
        && root.get("edges") != null
        && root.get("edges").isArray()
        && root.get("labels") != null
        && root.get("labels").isArray()) {
      root.set("matrix", matrixFromEdges(root));
    }
    // Etiquetas ausentes pero matriz cuadrada presente: A, B, C…
    if (root.get("labels") == null
        && root.get("matrix") != null
        && root.get("matrix").get("data") != null) {
      ArrayNode labels = mapper.createArrayNode();
      int n = root.get("matrix").get("data").size();
      for (int i = 0; i < n; i++)
        labels.add(i < 26 ? String.valueOf((char) ('A' + i)) : "V" + (i + 1));
      root.set("labels", labels);
    }
  }

  private boolean isIncidenceShaped(ObjectNode root, ObjectNode m) {
    JsonNode data = m.get("data");
    JsonNode labels = root.get("labels");
    if (data == null || !data.isArray() || data.isEmpty() || labels == null) return false;
    int rows = data.size();
    int cols = data.get(0).size();
    return rows == labels.size() && cols != rows;
  }

  private ObjectNode matrixFromEdges(ObjectNode root) {
    ArrayNode labels = (ArrayNode) root.get("labels");
    int n = labels.size();
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < n; i++) index.put(labels.get(i).asText(), i);
    boolean directed = root.get("directed").asBoolean();
    boolean weighted = root.get("weighted").asBoolean();
    int[][] data = new int[n][n];
    for (JsonNode e : root.get("edges")) {
      Integer a = endpoint(e.get("from") != null ? e.get("from") : e.get("source"), index, n);
      Integer b = endpoint(e.get("to") != null ? e.get("to") : e.get("target"), index, n);
      if (a == null || b == null) continue;
      int w = 1;
      if (e.get("weight") != null && e.get("weight").isNumber()) {
        w = e.get("weight").asInt();
        weighted = weighted || w != 1;
      }
      data[a][b] = w == 0 ? 1 : w;
      if (!directed) data[b][a] = data[a][b];
    }
    root.put("weighted", weighted);
    ObjectNode m = mapper.createObjectNode();
    m.put("kind", "adjacency");
    ArrayNode rows = mapper.createArrayNode();
    for (int[] r : data) {
      ArrayNode row = mapper.createArrayNode();
      for (int v : r) row.add(v);
      rows.add(row);
    }
    m.set("data", rows);
    return m;
  }

  private static Integer endpoint(JsonNode ref, Map<String, Integer> index, int n) {
    if (ref == null || ref.isNull()) return null;
    if (ref.isInt() && ref.asInt() >= 0 && ref.asInt() < n) return ref.asInt();
    Integer byLabel = index.get(ref.asText());
    if (byLabel != null) return byLabel;
    Integer parsed = parseInt(ref.asText());
    return parsed != null && parsed >= 0 && parsed < n ? parsed : null;
  }

  // ── Listas, tablas, pilas y colas ────────────────────────────────────────

  private void normalizeLinkedList(ObjectNode root, String rawTypeKey) {
    String subtype = text(root.get("subtype"));
    if (subtype == null && rawTypeKey.startsWith("singly")) subtype = "singly";
    if (subtype == null && rawTypeKey.startsWith("doubly")) subtype = "doubly";
    if (subtype != null) {
      String k = subtype.trim().toLowerCase(Locale.ROOT);
      subtype = LIST_SUBTYPES.getOrDefault(k, k);
      root.put("subtype", subtype);
    }
    pickValues(root);
  }

  private void normalizeHashTable(ObjectNode root) {
    if (root.get("size") == null || !root.get("size").isNumber()) {
      for (String f : List.of("size", "buckets", "capacity", "bucketCount", "slots")) {
        JsonNode v = root.get(f);
        if (v != null) {
          Integer n = v.isNumber() ? v.asInt() : parseInt(v.asText());
          if (n != null) {
            root.put("size", n);
            break;
          }
        }
      }
    }
    pickValues(root);
    if (text(root.get("hashFunction")) == null) root.put("hashFunction", "modular");
  }

  private void normalizeSequence(ObjectNode root) {
    pickValues(root);
  }

  private void pickValues(ObjectNode root) {
    for (String f : List.of("values", "elements", "items", "data", "keys", "nodes")) {
      JsonNode v = root.get(f);
      if (v != null && v.isArray()) {
        root.set("values", intArray((ArrayNode) v));
        return;
      }
    }
  }

  // ── Utilidades ───────────────────────────────────────────────────────────

  /** Convierte «"7"», 7.0 u objetos {value: 7} en enteros; descarta lo que no lo sea. */
  private ArrayNode intArray(ArrayNode in) {
    ArrayNode out = mapper.createArrayNode();
    for (JsonNode v : in) {
      Integer n = null;
      if (v.isNumber()) n = v.asInt();
      else if (v.isTextual()) n = parseInt(v.asText());
      else if (v.isObject()) {
        JsonNode inner = v.get("value") != null ? v.get("value") : v.get("label");
        if (inner != null) n = inner.isNumber() ? inner.asInt() : parseInt(inner.asText());
      }
      if (n != null) out.add(n);
    }
    return out;
  }

  private ArrayNode boolToInt(ArrayNode rows) {
    ArrayNode out = mapper.createArrayNode();
    for (JsonNode row : rows) {
      if (!row.isArray()) {
        out.add(row);
        continue;
      }
      ArrayNode r = mapper.createArrayNode();
      for (JsonNode cell : row) {
        if (cell.isBoolean()) r.add(cell.asBoolean() ? 1 : 0);
        else if (cell.isNumber()) r.add(cell.asInt());
        else if (cell.isTextual()) {
          Integer n = parseInt(cell.asText());
          r.add(n == null ? 0 : n);
        } else r.add(0);
      }
      out.add(r);
    }
    return out;
  }

  private static String text(JsonNode n) {
    return n == null || n.isNull() ? null : n.asText();
  }

  private static Integer parseInt(String s) {
    if (s == null) return null;
    try {
      return (int) Math.round(Double.parseDouble(s.trim()));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
