package com.vista.pdg.service.llm.def;

import com.vista.pdg.model.contract.def.StructureContract;

public interface LlmAdapter {

  StructureContract generate(String userPrompt);

  /**
   * Genera con el contexto de la sesión de trabajo (HU-32). Por defecto lo ignora, de modo que un
   * adaptador que no sepa refinar sigue siendo válido y se comporta como antes.
   */
  default StructureContract generate(String userPrompt, ConversationContext context) {
    return generate(userPrompt);
  }
}
