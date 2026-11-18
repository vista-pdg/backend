package com.vista.pdg.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.assistant.service.AssistantScopeService;
import com.vista.pdg.controller.dto.GenerateRequest;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.llm.def.ConversationContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Recorre, para la instrucción de un escenario, el mismo camino que {@code POST /api/generate}
 * (HU-35 · CA-2): alcance del asistente, constructor y validador de contratos, generador y layout.
 * Lo único que no hace es llamar al modelo; en su lugar usa el contrato que la instrucción
 * describe, construido con los mismos datos. Si alguna etapa lo rechaza, el escenario no llega al
 * banco.
 */
@Component
@RequiredArgsConstructor
public class QuizScenarioCheck {
  private final AssistantScopeService scope;
  private final ObjectMapper mapper;
  private final com.vista.pdg.service.sdd.impl.ContractBuilder contracts;
  private final GeneratorDispatcher generators;
  private final LayoutDispatcher layouts;

  /** La estructura que el chat dejaría en el lienzo, o vacío si alguna etapa la rechaza. */
  public Optional<StructureResponse> structure(
      String prompt, String type, String subtype, StructureContract expected) {
    try {
      GenerateRequest request = new GenerateRequest(prompt, type, subtype);
      scope.validate(request, ConversationContext.empty());
      StructureContract contract = contracts.build(mapper.writeValueAsString(expected));
      scope.verifyResult(request, contract);
      GeneratedStructure structure = generators.dispatch(contract);
      if (structure.nodes() == null || structure.nodes().isEmpty()) return Optional.empty();
      return Optional.of(StructureResponse.of(contract, structure, layouts.compute(structure)));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
