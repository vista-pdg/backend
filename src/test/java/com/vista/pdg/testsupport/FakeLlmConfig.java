package com.vista.pdg.testsupport;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.service.llm.def.LlmAdapter;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Sustituye a Gemini en las pruebas.
 *
 * <p>Devuelve siempre el mismo grafo de tres vértices. Lo que se prueba alrededor de {@code
 * /api/generate} —que exija sesión, que persista el evento seudonimizado— no depende de lo que el
 * modelo responda, y llamar al modelo real gastaría cuota, dependería de la red y haría las pruebas
 * no deterministas.
 */
@TestConfiguration
public class FakeLlmConfig {

  public static final List<String> LABELS = List.of("A", "B", "C");

  @Bean
  @Primary
  public LlmAdapter fakeLlmAdapter() {
    return prompt ->
        new GraphContract(
            "graph",
            null,
            false,
            false,
            LABELS,
            new GraphContract.MatrixDef(
                "adjacency", List.of(List.of(0, 1, 1), List.of(1, 0, 1), List.of(1, 1, 0)), null));
  }
}
