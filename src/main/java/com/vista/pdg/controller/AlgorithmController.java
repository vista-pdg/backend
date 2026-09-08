package com.vista.pdg.controller;

import com.vista.pdg.assistant.session.AssistantSessionService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.AlgorithmDispatcher;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.telemetry.service.TelemetryService;
import com.vista.pdg.telemetry.service.VisualizationMode;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/algorithm")
public class AlgorithmController {

  private final AlgorithmDispatcher dispatcher;
  private final TelemetryService telemetryService;
  private final AssistantSessionService sessionService;

  public AlgorithmController(
      AlgorithmDispatcher dispatcher,
      TelemetryService telemetryService,
      AssistantSessionService sessionService) {
    this.dispatcher = dispatcher;
    this.telemetryService = telemetryService;
    this.sessionService = sessionService;
  }

  /** HU-19 · CA-4: el catálogo es del servidor, idéntico para cualquier modo de visualización. */
  @GetMapping("/catalog")
  public List<AlgorithmDescriptor> catalog() {
    return dispatcher.catalog();
  }

  @PostMapping("/steps")
  public ResponseEntity<StepsResponse> steps(
      @RequestBody AlgorithmRequest req,
      @RequestHeader(value = VisualizationMode.HEADER, required = false) String mode,
      @AuthenticationPrincipal User user) {
    StepsResponse response = dispatcher.run(req);
    // HU-18 · CA-7: la ejecución se registra con el modo de visualización del cliente, y sólo si
    // produjo un rastro; una petición rechazada no es una ejecución.
    if (!response.error()) {
      // HU-21 · CA-2: qué algoritmo, sobre qué tipo de estructura y con cuántos pasos.
      telemetryService.recordAlgorithm(
          user,
          req.type(),
          req.subtype(),
          req.operation(),
          finalNodeCount(response.steps()),
          response.steps() == null ? null : response.steps().size(),
          mode,
          sessionService.ensureSessionId(user.getId()));
    }
    return ResponseEntity.ok(response);
  }

  private static int finalNodeCount(List<AlgorithmStep> steps) {
    return steps == null || steps.isEmpty() ? 0 : steps.getLast().nodes().size();
  }
}
