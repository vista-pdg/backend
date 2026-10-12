package com.vista.pdg.telemetry.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/** Fechas civiles inclusivas; la consulta usa [inicio, día siguiente al fin). */
public record AnalyticsFilter(String courseCode, LocalDate from, LocalDate to) {
  public static final ZoneId ZONE = ZoneId.of("America/Bogota");
  private static final Set<String> FIELDS = Set.of("courseCode", "from", "to");

  public static AnalyticsFilter parse(Map<String, String> params) {
    if (!FIELDS.containsAll(params.keySet())) {
      throw AnalyticsRequestException.invalid("Solo se permite filtrar por curso y fechas");
    }
    String course = params.get("courseCode");
    if (course == null || !course.matches("[A-Za-z0-9_-]{1,32}")) {
      throw AnalyticsRequestException.invalid("Selecciona un curso válido");
    }
    try {
      LocalDate from = LocalDate.parse(params.getOrDefault("from", ""));
      LocalDate to = LocalDate.parse(params.getOrDefault("to", ""));
      if (from.getYear() < 1 || to.getYear() > 9999 || to.isBefore(from)) {
        throw AnalyticsRequestException.invalid(
            "La fecha final debe ser igual o posterior a la inicial");
      }
      // El código es la clave exacta publicada por el catálogo, que no impone mayúsculas.
      return new AnalyticsFilter(course, from, to);
    } catch (DateTimeException e) {
      throw AnalyticsRequestException.invalid("Indica fechas válidas con formato AAAA-MM-DD");
    }
  }

  public Instant start() {
    return from.atStartOfDay(ZONE).toInstant();
  }

  public Instant endExclusive() {
    return to.plusDays(1).atStartOfDay(ZONE).toInstant();
  }
}
