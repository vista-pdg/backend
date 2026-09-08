package com.vista.pdg.service.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.UnsupportedStructureException;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.llm.def.ConversationContext;
import com.vista.pdg.service.llm.def.LlmAdapter;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Sustituto determinista de Gemini para el perfil {@code e2e}.
 *
 * <p>Las pruebas de extremo a extremo y la integración continua necesitan que {@code /api/generate}
 * responda siempre igual, sin clave de API, sin red y sin gastar cuota: lo que verifican —que el
 * asistente exija sesión, que la generación quede registrada por cohorte— no depende de lo que el
 * modelo conteste. Fuera de ese perfil este bean no existe, y arrancar con él activo deja una
 * advertencia inconfundible en el log.
 */
@Service
@Primary
@Profile("e2e")
public class StubLlmAdapter implements LlmAdapter {

  private static final Logger log = LoggerFactory.getLogger(StubLlmAdapter.class);

  private final ObjectMapper mapper;

  public StubLlmAdapter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @PostConstruct
  void warn() {
    log.warn("Perfil e2e activo: /api/generate responde con un grafo fijo, NO llama a Gemini");
  }

  /** «grafo de 12 nodos», «ciclo con 8 vértices»: el número manda; sin número, K3. */
  private static final Pattern SIZE = Pattern.compile("(\\d{1,2})\\s*(v[ée]rtices|nodos)");

  private static final Pattern NUMBERS = Pattern.compile("-?\\d+");
  private static final Pattern STACK = Pattern.compile("\\b(pila|stack)\\b");
  private static final Pattern QUEUE = Pattern.compile("\\b(cola|queue)\\b");
  private static final Pattern TREE = Pattern.compile("\\b([aá]rbol|tree|bst|avl)\\b");

  /** Refinamientos que el stub sabe aplicar (HU-32). */
  private static final Pattern INSERT =
      Pattern.compile("\\b(inserta|insertar|agrega|a[ñn]ade|insert|add)\\b");

  private static final Pattern DIRECTED = Pattern.compile("\\b(dirigido|directed)\\b");

  /**
   * HU-21 · CA-3: estructuras que el syllabus no cubre. El modelo real acabaría agotando los
   * intentos o produciendo un contrato que el validador rechaza; el stub lo resuelve de una vez y
   * de forma determinista para que el escenario de «fuera de alcance» se pueda probar sin Gemini.
   */
  private static final Pattern OUT_OF_SYLLABUS =
      Pattern.compile("\\b(trie|rojinegro|rojo-negro|b\\+|skip ?list|fractal|hiperb[oó]lico)\\b");

  /**
   * Refinamiento determinista (HU-32). Con una estructura vigente en la sesión, el stub aplica los
   * dos cambios que los escenarios necesitan —insertar valores y volver dirigido un grafo— sobre el
   * contrato anterior; cualquier otra instrucción se interpreta como estructura nueva, igual que
   * haría el modelo.
   */
  @Override
  public StructureContract generate(String userPrompt, ConversationContext context) {
    String lower = userPrompt == null ? "" : userPrompt.toLowerCase();
    if (context == null || context.currentContract() == null || describesNewStructure(lower)) {
      return generate(userPrompt);
    }
    try {
      JsonNode current = mapper.readTree(context.currentContract());
      String type = current.path("type").asText("");
      if (INSERT.matcher(lower).find()) {
        List<Integer> added = listedValues(lower, List.of());
        if (!added.isEmpty()) {
          if (type.equals("tree")) return treeWith(current, added);
          if (type.equals("stack") || type.equals("queue"))
            return sequenceWith(current, type, added);
        }
      }
      if (type.equals("graph") && DIRECTED.matcher(lower).find()) {
        return directedFrom(current);
      }
      // No sé refinarlo: devuelvo la estructura vigente sin cambios, que es lo honesto para un
      // stub.
      return mapper.treeToValue(current, StructureContract.class);
    } catch (Exception e) {
      log.warn(
          "Stub: no se pudo refinar sobre el contexto ({}), se genera de cero", e.getMessage());
      return generate(userPrompt);
    }
  }

  /** Nombrar otra familia es empezar de cero, aunque haya sesión. */
  private static boolean describesNewStructure(String lower) {
    return STACK.matcher(lower).find()
        || QUEUE.matcher(lower).find()
        || TREE.matcher(lower).find()
        || SIZE.matcher(lower).find();
  }

  private TreeContract treeWith(JsonNode current, List<Integer> added) {
    List<Integer> values = new ArrayList<>();
    JsonNode ops = current.path("operations");
    if (ops.isArray() && !ops.isEmpty()) {
      for (JsonNode v : ops.get(0).path("values")) values.add(v.asInt());
    }
    values.addAll(added);
    String subtype = current.path("subtype").asText("bst");
    return new TreeContract(
        "tree", null, subtype, List.of(new TreeContract.Operation("insert", values)), null);
  }

  private StructureContract sequenceWith(JsonNode current, String type, List<Integer> added) {
    List<Integer> values = new ArrayList<>();
    for (JsonNode v : current.path("values")) values.add(v.asInt());
    values.addAll(added);
    return type.equals("stack")
        ? new StackContract("stack", null, values)
        : new QueueContract("queue", null, values);
  }

  /** Mismas etiquetas y mismas aristas, ahora con un solo sentido por par. */
  private GraphContract directedFrom(JsonNode current) {
    List<String> labels = new ArrayList<>();
    for (JsonNode l : current.path("labels")) labels.add(l.asText());
    List<List<Integer>> data = new ArrayList<>();
    for (JsonNode row : current.path("matrix").path("data")) {
      List<Integer> r = new ArrayList<>();
      for (JsonNode cell : row) r.add(cell.asInt());
      data.add(r);
    }
    for (int i = 0; i < data.size(); i++) {
      for (int j = 0; j < i; j++) {
        if (data.get(i).get(j) != 0 && data.get(j).get(i) != 0) data.get(i).set(j, 0);
      }
    }
    return new GraphContract(
        "graph",
        null,
        true,
        current.path("weighted").asBoolean(false),
        labels,
        new GraphContract.MatrixDef("adjacency", data, null));
  }

  @Override
  public StructureContract generate(String userPrompt) {
    String lower = userPrompt == null ? "" : userPrompt.toLowerCase();
    if (OUT_OF_SYLLABUS.matcher(lower).find()) {
      throw new UnsupportedStructureException(
          "La estructura solicitada no está contemplada en el syllabus");
    }
    // HU-32: «árbol con inserción de 1, 2, 3» — el stub también cubre la familia de árboles.
    if (TREE.matcher(lower).find()) {
      String subtype = lower.contains("avl") ? "avl" : "bst";
      List<Integer> values = listedValues(lower, List.of(10, 5, 15));
      return new TreeContract(
          "tree", null, subtype, List.of(new TreeContract.Operation("insert", values)), null);
    }
    // HU-19: «pila con 3, 42, 8, 17» / «cola con 5, 9, 1, 14». Sin números, cuatro por defecto.
    if (STACK.matcher(lower).find()) {
      return new StackContract("stack", null, listedValues(lower, List.of(3, 42, 8, 17)));
    }
    if (QUEUE.matcher(lower).find()) {
      return new QueueContract("queue", null, listedValues(lower, List.of(5, 9, 1, 14)));
    }
    int n = requestedSize(userPrompt);
    if (n <= 3) {
      return new GraphContract(
          "graph",
          null,
          false,
          false,
          List.of("A", "B", "C"),
          new GraphContract.MatrixDef(
              "adjacency", List.of(List.of(0, 1, 1), List.of(1, 0, 1), List.of(1, 1, 0)), null));
    }
    // Un ciclo C_n: n nodos, n aristas, determinista. Suficiente para las pruebas que necesitan
    // "un grafo con 12 nodos" (HU-18 · CA-3) sin depender del modelo.
    List<String> labels = new ArrayList<>();
    List<List<Integer>> matrix = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      labels.add("V" + (i + 1));
      List<Integer> row = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        boolean adjacent = j == (i + 1) % n || i == (j + 1) % n;
        row.add(adjacent ? 1 : 0);
      }
      matrix.add(row);
    }
    return new GraphContract(
        "graph",
        null,
        false,
        false,
        labels,
        new GraphContract.MatrixDef("adjacency", matrix, null));
  }

  private static List<Integer> listedValues(String prompt, List<Integer> fallback) {
    List<Integer> values = new ArrayList<>();
    Matcher m = NUMBERS.matcher(prompt);
    while (m.find()) values.add(Integer.parseInt(m.group()));
    return values.isEmpty() ? fallback : values;
  }

  private static int requestedSize(String prompt) {
    if (prompt == null) return 3;
    Matcher m = SIZE.matcher(prompt.toLowerCase());
    if (!m.find()) return 3;
    return Math.min(20, Math.max(3, Integer.parseInt(m.group(1))));
  }
}
