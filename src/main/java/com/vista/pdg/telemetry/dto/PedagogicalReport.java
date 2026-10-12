package com.vista.pdg.telemetry.dto;

import com.vista.pdg.academic.dto.CourseDto;
import java.time.LocalDate;
import java.util.List;

/** Contrato público: nunca contiene identidades, sesiones ni conteos de participantes. */
public record PedagogicalReport(
    CourseDto course,
    LocalDate from,
    LocalDate to,
    String timezone,
    int minimumParticipants,
    double retryThreshold,
    int retryMinAttempts,
    int retryWindowSeconds,
    Long totalRequests,
    List<StructureMetric> structures,
    List<AlgorithmMetric> algorithms,
    List<RetryMetric> retries) {

  public enum Status {
    AVAILABLE,
    SUPPRESSED,
    UNAVAILABLE
  }

  public record StructureMetric(
      String structure, Status status, Long frequency, Double percentage) {}

  public record AlgorithmMetric(
      String algorithm,
      Status status,
      Long executions,
      Status stepsStatus,
      Double averageCompletedSteps) {}

  public record RetryMetric(
      String structure,
      Status status,
      Long sessions,
      Long sessionsWithRetries,
      Double retryRate,
      Boolean highlighted) {}
}
