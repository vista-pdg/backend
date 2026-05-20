package com.vista.pdg.controller;

import com.vista.pdg.controller.dto.GenerateRequest;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.llm.def.LlmAdapter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class StructureController {

    private final LlmAdapter llmAdapter;
    private final GeneratorDispatcher generatorDispatcher;
    private final LayoutDispatcher layoutDispatcher;

    public StructureController(LlmAdapter llmAdapter,
                                GeneratorDispatcher generatorDispatcher,
                                LayoutDispatcher layoutDispatcher) {
        this.llmAdapter          = llmAdapter;
        this.generatorDispatcher = generatorDispatcher;
        this.layoutDispatcher    = layoutDispatcher;
    }

    @PostMapping("/generate")
    public ResponseEntity<StructureResponse> generate(@RequestBody GenerateRequest req) {
        StructureContract contract     = llmAdapter.generate(req.prompt());
        GeneratedStructure structure   = generatorDispatcher.dispatch(contract);
        Map<String, Vec3> positions    = layoutDispatcher.compute(structure);
        return ResponseEntity.ok(StructureResponse.of(contract, structure, positions));
    }
}
