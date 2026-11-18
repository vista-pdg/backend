package com.vista.pdg.telemetry.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * Curso obligatorio; fechas civiles inclusivas y quiz, opcionales (HU-36 · CA-5). Sin fechas se usa
 * todo el periodo del curso. La consulta usa [inicio, día siguiente al fin).
 */
public record AnalyticsFilter(String courseCode, LocalDate from, LocalDate to, Long quizId) {
  public static final ZoneId ZONE = ZoneId.of("America/Bogota");
  private static final Set<String> FIELDS = Set.of("courseCode", "from", "to", "quizId");

  public AnalyticsFilter(String courseCode, LocalDate from, LocalDate to) {
    this(courseCode, from, to, null);
  }

  public static AnalyticsFilter parse(Map<String, String> params) {
    if (!FIELDS.containsAll(params.keySet())) {
      throw AnalyticsRequestException.invalid("Solo se permite filtrar por curso, fechas y quiz");
    }
    String course = params.get("courseCode");
    if (course == null || !course.matches("[A-Za-z0-9_-]{1,32}")) {
      throw AnalyticsRequestException.invalid("Selecciona un curso válido");
    }
    LocalDate from = date(params.get("from"));
    LocalDate to = date(params.get("to"));
    if ((from != null && from.getYear() < 1) || (to != null && to.getYear() > 9999)) {
      throw AnalyticsRequestException.invalid("Indica fechas válidas con formato AAAA-MM-DD");
    }
    if (from != null && to != null && to.isBefore(from)) {
      throw AnalyticsRequestException.invalid(
          "La fecha final debe ser igual o posterior a la inicial");
    }
    String quiz = params.get("quizId");
    Long quizId = null;
    if (quiz != null && !quiz.isBlank()) {
      if (!quiz.matches("[0-9]{1,18}")) {
        throw AnalyticsRequestException.invalid("Selecciona un quiz válido");
      }
      quizId = Long.valueOf(quiz);
    }
    // El código es la clave exacta publicada por el catálogo, que no impone mayúsculas.
    return new AnalyticsFilter(course, from, to, quizId);
  }

  private static LocalDate date(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeException e) {
      throw AnalyticsRequestException.invalid("Indica fechas válidas con formato AAAA-MM-DD");
    }
  }

  public boolean wholeTerm() {
    return from == null && to == null;
  }

  /** Nulo: sin límite inferior. */
  public Instant start() {
    return from == null ? null : from.atStartOfDay(ZONE).toInstant();
  }

  /** Nulo: sin límite superior. */
  public Instant endExclusive() {
    return to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
  }
}
