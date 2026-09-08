package com.vista.pdg.telemetry.controller;

import com.vista.pdg.assistant.session.AssistantSessionService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.telemetry.dto.ClientEventRequest;
import com.vista.pdg.telemetry.service.InteractionEvent;
import com.vista.pdg.telemetry.service.StructureKind;
import com.vista.pdg.telemetry.service.TelemetryService;
import com.vista.pdg.telemetry.service.VisualizationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Punto de captura del cliente (HU-21 · CA-2): el único hecho que el navegador conoce y el servidor
 * no es que el estudiante recorrió un algoritmo <b>hasta el final</b> y cuántos pasos dio.
 *
 * <p>Vive fuera de {@code /api/analytics/**} —que es sólo del docente— porque escribir el propio
 * evento es cosa del estudiante y leer los agregados no. Responde 202 siempre que el cuerpo sea
 * reconocible: la telemetría no debe hacer fallar nada en el cliente.
 */
@RestController
@RequestMapping("/api/assistant/events")
@RequiredArgsConstructor
public class AnalyticsEventController {

  private final TelemetryService telemetryService;
  private final AssistantSessionService sessionService;

  @PostMapping
  public ResponseEntity<Void> report(
      @RequestBody ClientEventRequest req,
      @RequestHeader(value = VisualizationMode.HEADER, required = false) String mode,
      @AuthenticationPrincipal User user) {
    if (!ClientEventRequest.ALGORITHM_COMPLETED.equals(req.event())) {
      // Un evento que no reconocemos no es un error del estudiante: se ignora y se sigue.
      return ResponseEntity.accepted().build();
    }
    telemetryService.record(
        InteractionEvent.of(user, TelemetryService.KIND_ALGORITHM_COMPLETED)
            .structureKind(StructureKind.of(req.type(), req.subtype()))
            .subtype(req.subtype())
            .algorithm(req.algorithm())
            .nodeCount(req.nodeCount() == null ? 0 : Math.max(0, req.nodeCount()))
            .stepCount(req.stepCount() == null ? null : Math.max(0, req.stepCount()))
            .source(InteractionEvent.Source.CATALOGO_ALGORITMOS)
            .outcome(InteractionEvent.Outcome.EXITO)
            .visualizationMode(mode)
            .sessionId(sessionService.ensureSessionId(user.getId()))
            .build());
    return ResponseEntity.accepted().build();
  }
}
