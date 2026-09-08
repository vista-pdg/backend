package com.vista.pdg.controller;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.controller.dto.GenerateRequest;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.llm.def.LlmAdapter;
import com.vista.pdg.telemetry.service.TelemetryService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StructureController {

  private final LlmAdapter llmAdapter;
  private final GeneratorDispatcher generatorDispatcher;
  private final LayoutDispatcher layoutDispatcher;
  private final TelemetryService telemetryService;

  public StructureController(
      LlmAdapter llmAdapter,
      GeneratorDispatcher generatorDispatcher,
      LayoutDispatcher layoutDispatcher,
      TelemetryService telemetryService) {
    this.llmAdapter = llmAdapter;
    this.generatorDispatcher = generatorDispatcher;
    this.layoutDispatcher = layoutDispatcher;
    this.telemetryService = telemetryService;
  }

  @PostMapping("/generate")
  public ResponseEntity<StructureResponse> generate(
      @RequestBody GenerateRequest req, @AuthenticationPrincipal User user) {
    StructureContract contract = llmAdapter.generate(req.prompt());
    GeneratedStructure structure = generatorDispatcher.dispatch(contract);
    Map<String, Vec3> positions = layoutDispatcher.compute(structure);
    // HU-16 CA-5: el evento se registra seudonimizado y sólo si la generación tuvo éxito.
    if (user != null) telemetryService.recordGeneration(user, structure);
    return ResponseEntity.ok(StructureResponse.of(contract, structure, positions));
  }
}
