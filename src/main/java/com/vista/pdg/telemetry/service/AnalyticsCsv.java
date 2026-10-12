package com.vista.pdg.telemetry.service;

import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.dto.PedagogicalReport.Status;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Recibe exclusivamente el reporte ya protegido. No tiene acceso a eventos ni repositorios. */
public final class AnalyticsCsv {
  private AnalyticsCsv() {}

  public static String render(PedagogicalReport report) {
    StringBuilder csv = new StringBuilder("\ufeff");
    row(
        csv,
        "curso",
        "periodo",
        "desde",
        "hasta",
        "vista",
        "tema",
        "estado",
        "frecuencia",
        "porcentaje",
        "estado_pasos",
        "promedio_pasos_completados",
        "sesiones",
        "sesiones_con_reintentos",
        "tasa_reintento",
        "supera_umbral",
        "umbral_reintento");
    for (var r : report.structures()) {
      row(
          csv,
          report.course().code(),
          report.course().termCode(),
          report.from(),
          report.to(),
          "Estructuras",
          r.structure(),
          label(r.status()),
          r.frequency(),
          r.percentage(),
          null,
          null,
          null,
          null,
          null,
          null,
          report.retryThreshold());
    }
    for (var r : report.algorithms()) {
      row(
          csv,
          report.course().code(),
          report.course().termCode(),
          report.from(),
          report.to(),
          "Algoritmos",
          r.algorithm(),
          label(r.status()),
          r.executions(),
          null,
          label(r.stepsStatus()),
          r.averageCompletedSteps(),
          null,
          null,
          null,
          null,
          report.retryThreshold());
    }
    for (var r : report.retries()) {
      row(
          csv,
          report.course().code(),
          report.course().termCode(),
          report.from(),
          report.to(),
          "Reintentos",
          r.structure(),
          label(r.status()),
          null,
          null,
          null,
          null,
          r.sessions(),
          r.sessionsWithRetries(),
          r.retryRate(),
          r.highlighted(),
          report.retryThreshold());
    }
    return csv.toString();
  }

  private static String label(Status status) {
    return switch (status) {
      case AVAILABLE -> "Disponible";
      case SUPPRESSED -> AnalyticsDisclosure.INSUFFICIENT;
      case UNAVAILABLE -> "Sin medición de pasos para todas las ejecuciones del periodo";
    };
  }

  private static void row(StringBuilder csv, Object... values) {
    csv.append(Arrays.stream(values).map(AnalyticsCsv::cell).collect(Collectors.joining(",")))
        .append("\r\n");
  }

  static String cell(Object value) {
    if (value == null) return "";
    String text = value.toString();
    String trimmed = text.stripLeading();
    if (value instanceof String && !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
      text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
