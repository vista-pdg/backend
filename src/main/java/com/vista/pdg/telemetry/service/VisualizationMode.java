package com.vista.pdg.telemetry.service;

import java.util.Locale;

/**
 * Modo de visualización que el cliente informa en la cabecera {@code X-Visualization-Mode} (HU-18).
 *
 * <p>Se acepta {@code 2D} o {@code 3D} sin distinguir mayúsculas; cualquier otro valor —o la
 * ausencia de cabecera— se registra como desconocido (nulo) en vez de rechazar la petición: la
 * telemetría es un subproducto y no debe condicionar la operación que la genera.
 */
public final class VisualizationMode {

  public static final String HEADER = "X-Visualization-Mode";

  private VisualizationMode() {}

  /** Devuelve {@code "2D"}, {@code "3D"} o {@code null}. */
  public static String normalize(String header) {
    if (header == null) return null;
    String v = header.trim().toUpperCase(Locale.ROOT);
    return v.equals("2D") || v.equals("3D") ? v : null;
  }
}
