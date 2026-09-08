package com.vista.pdg.service.llm.impl;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.def.StructureContract;
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

  @PostConstruct
  void warn() {
    log.warn("Perfil e2e activo: /api/generate responde con un grafo fijo, NO llama a Gemini");
  }

  /** «grafo de 12 nodos», «ciclo con 8 vértices»: el número manda; sin número, K3. */
  private static final Pattern SIZE = Pattern.compile("(\\d{1,2})\\s*(v[ée]rtices|nodos)");

  private static final Pattern NUMBERS = Pattern.compile("-?\\d+");
  private static final Pattern STACK = Pattern.compile("\\b(pila|stack)\\b");
  private static final Pattern QUEUE = Pattern.compile("\\b(cola|queue)\\b");

  @Override
  public StructureContract generate(String userPrompt) {
    String lower = userPrompt == null ? "" : userPrompt.toLowerCase();
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
