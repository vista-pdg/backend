package com.vista.pdg.testsupport;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.llm.def.ConversationContext;
import com.vista.pdg.service.llm.def.LlmAdapter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

  /** Invocaciones al modelo. Permite afirmar que un 429 no produjo ninguna llamada facturable. */
  public static final AtomicInteger CALLS = new AtomicInteger();

  /**
   * Último contexto de sesión que recibió el adaptador (HU-32). Es lo que permite comprobar que la
   * memoria conversacional llega de verdad al modelo, sin depender de qué haría el modelo con ella.
   */
  public static final AtomicReference<ConversationContext> LAST_CONTEXT = new AtomicReference<>();

  @Bean
  @Primary
  public LlmAdapter fakeLlmAdapter() {
    return new LlmAdapter() {
      @Override
      public StructureContract generate(String userPrompt) {
        return generate(userPrompt, ConversationContext.empty());
      }

      @Override
      public StructureContract generate(String userPrompt, ConversationContext context) {
        CALLS.incrementAndGet();
        LAST_CONTEXT.set(context);
        return new GraphContract(
            "graph",
            null,
            false,
            false,
            LABELS,
            new GraphContract.MatrixDef(
                "adjacency", List.of(List.of(0, 1, 1), List.of(1, 0, 1), List.of(1, 1, 0)), null));
      }
    };
  }
}
