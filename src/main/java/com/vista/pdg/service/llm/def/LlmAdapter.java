package com.vista.pdg.service.llm.def;

import com.vista.pdg.model.contract.StructureContract;

public interface LlmAdapter {
    StructureContract generate(String userPrompt);
}
