package com.vista.pdg.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.config.GeminiProperties;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.service.PipelineFixtures;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.llm.def.LlmAdapter;
import com.vista.pdg.service.llm.impl.GeminiLlmAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Prueba <b>viva</b> contra Gemini, una instrucción por tipo de estructura. No es determinista y
 * consume créditos, así que no corre en CI ni en {@code make test}: sólo con
 *
 * <pre>
 * set -a; source .env; set +a; GEMINI_LIVE_TESTS=true ./mvnw -Dtest=GeminiLiveGenerationTest test
 * </pre>
 *
 * <p>Lo que afirma es lo que sí debe ser estable pase lo que pase con la redacción del modelo: el
 * tipo pedido y el número de nodos que el usuario dictó. La forma exacta del JSON la absorbe el
 * normalizador (probado en {@code ContractNormalizerTest}).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_TESTS", matches = "true")
class GeminiLiveGenerationTest {

  /** Clave y modelo del entorno (los mismos que {@code backend/.env}); sin contexto de Spring. */
  private final LlmAdapter llm =
      new GeminiLlmAdapter(
          new GeminiProperties(
              new GeminiProperties.Api(
                  System.getenv().getOrDefault("GEMINI_API_KEY", "change-me"),
                  System.getenv().getOrDefault("GEMINI_API_MODEL", "gemini-3.5-flash-lite"),
                  Boolean.parseBoolean(System.getenv().getOrDefault("GEMINI_VERTEX", "false")),
                  System.getenv().get("GEMINI_PROJECT"),
                  System.getenv().getOrDefault("GEMINI_LOCATION", "global"))),
          PipelineFixtures.contractBuilder());

  private final GeneratorDispatcher generators = PipelineFixtures.generators();

  @ParameterizedTest(name = "{0} → {1} con {2} nodos")
  @CsvSource(
      delimiter = '|',
      value = {
        "Genera un arbol ahora con insercion de 1, 2, 3 , 5 , 6 | tree | 5",
        "AVL tree insertando 10, 5, 15, 3, 7                    | tree | 5",
        "BST insertando 8, 3, 10, 1, 6, 14, 4, 7                | tree | 8",
        "Max-heap insertando 5, 3, 7, 1, 9, 2                   | tree | 6",
        "Grafo dirigido con 5 nodos en ciclo                    | graph | 5",
        "Grafo completo K4                                      | graph | 4",
        "Lista simple con [1, 2, 3, 4, 5]                       | linked-list | 5",
        "Lista circular con 4 nodos: 7, 14, 21, 28              | linked-list | 4",
        "Pila con 3, 42, 8, 17 (17 en el tope)                  | stack | 4",
        "Cola con 5, 9, 1, 14 (5 al frente)                     | queue | 4",
      })
  @DisplayName("el modelo produce el tipo y el tamaño pedidos")
  void modelProducesRequestedShape(String prompt, String type, int nodes) {
    StructureContract contract = llm.generate(prompt);
    assertThat(contract.type()).isEqualTo(type);
    GeneratedStructure structure = generators.dispatch(contract);
    assertThat(structure.nodes()).hasSize(nodes);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {"Tabla hash 7 cubetas con [15, 22, 35, 8, 43, 10] | hash-table | 13"})
  @DisplayName("tabla hash: cubetas + valores")
  void hashTable(String prompt, String type, int nodes) {
    modelProducesRequestedShape(prompt, type, nodes);
  }
}
