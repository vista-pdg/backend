package com.vista.pdg.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.llm.def.AbstractLlmAdapter;
import com.vista.pdg.service.llm.impl.GeminiLlmAdapter;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import com.vista.pdg.service.sdd.impl.ContractNormalizer;
import com.vista.pdg.service.sdd.impl.ContractValidator;
import com.vista.pdg.service.sdd.impl.validator.GraphContractValidator;
import com.vista.pdg.service.sdd.impl.validator.HashTableContractValidator;
import com.vista.pdg.service.sdd.impl.validator.LinkedListContractValidator;
import com.vista.pdg.service.sdd.impl.validator.QueueContractValidator;
import com.vista.pdg.service.sdd.impl.validator.StackContractValidator;
import com.vista.pdg.service.sdd.impl.validator.TreeContractValidator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Qué hace la cadena de reintentos con las respuestas que el modelo puede dar. */
class LlmAdapterRetryTest {

  private static ContractBuilder builder() {
    ObjectMapper mapper = new ObjectMapper();
    ContractValidator validator =
        new ContractValidator(
            new GraphContractValidator(),
            new TreeContractValidator(),
            new LinkedListContractValidator(),
            new HashTableContractValidator(),
            new StackContractValidator(),
            new QueueContractValidator());
    return new ContractBuilder(mapper, validator, new ContractNormalizer(mapper));
  }

  /** Adaptador que responde una secuencia fija y anota los prompts que recibe. */
  private static final class ScriptedAdapter extends AbstractLlmAdapter {
    final Iterator<Object> script;
    final List<String> prompts = new ArrayList<>();

    ScriptedAdapter(Object... responses) {
      super(builder());
      this.script = List.of(responses).iterator();
    }

    @Override
    protected String callModel(String systemPrompt, String userPrompt) {
      prompts.add(userPrompt);
      Object next = script.next();
      if (next instanceof RuntimeException e) throw e;
      return (String) next;
    }
  }

  @Test
  @DisplayName("una respuesta laxa pero normalizable se acepta al primer intento")
  void looseButNormalizableAnswerIsAccepted() {
    ScriptedAdapter a =
        new ScriptedAdapter("```json\n{\"type\":\"bst\",\"values\":[1,2,3,5,6]}\n```");
    StructureContract c = a.generate("Genera un arbol con insercion de 1, 2, 3, 5, 6");
    assertThat(c).isInstanceOf(TreeContract.class);
    assertThat(((TreeContract) c).operations().getFirst().values()).containsExactly(1, 2, 3, 5, 6);
    assertThat(a.prompts).hasSize(1);
  }

  @Test
  @DisplayName(
      "un contrato inválido se reintenta con el error como corrección, y el segundo intento vale")
  void invalidContractIsRetriedWithFeedback() {
    ScriptedAdapter a =
        new ScriptedAdapter(
            "{\"type\":\"tree\",\"subtype\":\"bst\"}",
            "{\"type\":\"tree\",\"subtype\":\"bst\",\"operations\":[{\"op\":\"insert\",\"values\":[2,1,3]}]}");
    StructureContract c = a.generate("árbol");
    assertThat(c).isInstanceOf(TreeContract.class);
    assertThat(a.prompts).hasSize(2);
    assertThat(a.prompts.get(1))
        .contains("previous response failed")
        .contains("operations or pre-built nodes");
  }

  @Test
  @DisplayName("tres respuestas inválidas agotan los intentos con el detalle de cada uno")
  void threeInvalidAnswersExhaust() {
    ScriptedAdapter a = new ScriptedAdapter("no json", "{\"type\":\"nope\"}", "{}");
    assertThatThrownBy(() -> a.generate("x"))
        .isInstanceOf(LlmExhaustedException.class)
        .satisfies(e -> assertThat(((LlmExhaustedException) e).attempts()).hasSize(3));
  }

  @Test
  @DisplayName("créditos o cuota agotados: no se reintenta y el error llega tal cual")
  void unavailableIsNotRetried() {
    ScriptedAdapter a =
        new ScriptedAdapter(
            new LlmUnavailableException("sin créditos", null),
            "{\"type\":\"stack\",\"values\":[1]}");
    assertThatThrownBy(() -> a.generate("x"))
        .isInstanceOf(LlmUnavailableException.class)
        .hasMessage("sin créditos");
    assertThat(a.prompts).hasSize(1);
  }

  @Test
  @DisplayName(
      "el adaptador de Gemini clasifica 429, 401/403, 404 y 503 como no disponible; el resto no")
  void geminiClassifiesProviderFailures() {
    assertThat(
            GeminiLlmAdapter.unavailableReason(
                new RuntimeException(
                    "429 Too Many Requests. Your prepayment credits are depleted")))
        .contains("cuota o créditos");
    assertThat(
            GeminiLlmAdapter.unavailableReason(
                new RuntimeException("403 PERMISSION_DENIED: API key not valid")))
        .contains("clave");
    assertThat(
            GeminiLlmAdapter.unavailableReason(
                new RuntimeException("404 NOT_FOUND: model x is not found")))
        .contains("modelo");
    assertThat(
            GeminiLlmAdapter.unavailableReason(new RuntimeException("503 UNAVAILABLE: overloaded")))
        .contains("503");
    assertThat(GeminiLlmAdapter.unavailableReason(new RuntimeException("socket closed"))).isNull();
  }
}
