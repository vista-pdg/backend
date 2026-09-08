package com.vista.pdg.telemetry.controller;

import com.vista.pdg.telemetry.dto.AnalyticsSummary;
import com.vista.pdg.telemetry.dto.EventView;
import com.vista.pdg.telemetry.dto.RetrySequence;
import com.vista.pdg.telemetry.service.TelemetryService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restringido a TEACHER en {@code SecurityConfig}: un estudiante recibe 403 sin cuerpo con datos,
 * que es lo que CA-4 de la HU-16 exige. Sólo devuelve agregados.
 *
 * <p>La escritura de eventos del cliente vive en {@link AnalyticsEventController}, bajo otra ruta,
 * precisamente porque un estudiante sí debe poder escribir sus propios eventos y nunca leer los
 * agregados.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(2);
  private static final int DEFAULT_MIN_ATTEMPTS = 3;

  private final TelemetryService telemetryService;

  @GetMapping("/summary")
  public AnalyticsSummary summary() {
    return telemetryService.summary();
  }

  /**
   * HU-21 · CA-3: los últimos eventos, con el texto de los que no terminaron bien, para revisión
   * docente. Sin seudónimo: se revisa qué se pidió, no quién.
   */
  @GetMapping("/events")
  public List<EventView> events(
      @RequestParam(required = false) String outcome,
      @RequestParam(defaultValue = "50") int limit) {
    return telemetryService.recentEvents(outcome, limit);
  }

  /**
   * HU-21 · CA-4: secuencias de reintento. Los umbrales son parámetros porque «tres veces en dos
   * minutos» es una convención pedagógica, no una verdad: el docente podrá ajustarla.
   */
  @GetMapping("/retries")
  public List<RetrySequence> retries(
      @RequestParam(defaultValue = "" + DEFAULT_MIN_ATTEMPTS) int minAttempts,
      @RequestParam(defaultValue = "120") long windowSeconds) {
    return telemetryService.retrySequences(
        Math.max(2, minAttempts), Duration.ofSeconds(Math.max(1, windowSeconds)));
  }
}
