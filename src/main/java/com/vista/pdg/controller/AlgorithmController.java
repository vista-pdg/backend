package com.vista.pdg.controller;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.AvlStepsService;
import com.vista.pdg.telemetry.service.TelemetryService;
import com.vista.pdg.telemetry.service.VisualizationMode;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/algorithm")
public class AlgorithmController {

  private final AvlStepsService avlStepsService;
  private final TelemetryService telemetryService;

  public AlgorithmController(AvlStepsService avlStepsService, TelemetryService telemetryService) {
    this.avlStepsService = avlStepsService;
    this.telemetryService = telemetryService;
  }

  @PostMapping("/steps")
  public ResponseEntity<StepsResponse> steps(
      @RequestBody AlgorithmRequest req,
      @RequestHeader(value = VisualizationMode.HEADER, required = false) String mode,
      @AuthenticationPrincipal User user) {
    if ("tree".equals(req.type())
        && "avl".equals(req.subtype())
        && "insert".equals(req.operation())) {
      StepsResponse response = avlStepsService.generateAvlInsertSteps(req.values());
      // HU-18 · CA-7: la ejecución se registra con el modo de visualización del cliente, y sólo si
      // produjo un rastro; una petición rechazada no es una ejecución.
      if (!response.error()) {
        telemetryService.recordAlgorithm(
            user, req.type(), req.subtype(), finalNodeCount(response.steps()), mode);
      }
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.ok(
        StepsResponse.error(
            "Algoritmo no soportado: " + req.type() + "/" + req.subtype() + "/" + req.operation()));
  }

  private static int finalNodeCount(List<AlgorithmStep> steps) {
    return steps == null || steps.isEmpty() ? 0 : steps.getLast().nodes().size();
  }
}
