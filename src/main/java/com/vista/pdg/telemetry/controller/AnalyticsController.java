package com.vista.pdg.telemetry.controller;

import com.vista.pdg.telemetry.service.AnalyticsRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-06: las lecturas históricas sin cohorte permitían eludir la política de anonimato. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
  @GetMapping({"/summary", "/events", "/retries"})
  public void retired() {
    throw new AnalyticsRequestException(
        410,
        "ANALYTICS_ENDPOINT_RETIRED",
        "Esta consulta fue retirada. Utiliza el reporte agregado por curso y fechas.");
  }
}
