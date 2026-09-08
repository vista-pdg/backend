package com.vista.pdg.telemetry.controller;

import com.vista.pdg.telemetry.dto.AnalyticsSummary;
import com.vista.pdg.telemetry.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restringido a TEACHER en {@code SecurityConfig}: un estudiante recibe 403 sin cuerpo con datos,
 * que es lo que CA-4 de la HU-16 exige. Sólo devuelve agregados.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final TelemetryService telemetryService;

  @GetMapping("/summary")
  public AnalyticsSummary summary() {
    return telemetryService.summary();
  }
}
