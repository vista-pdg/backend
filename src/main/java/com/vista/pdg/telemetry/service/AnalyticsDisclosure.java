package com.vista.pdg.telemetry.service;

import static com.vista.pdg.telemetry.dto.PedagogicalReport.Status.*;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.dto.PedagogicalReport.*;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.*;
import java.util.Comparator;
import java.util.List;

/** Una única frontera de divulgación, compartida por JSON y CSV. */
public final class AnalyticsDisclosure {
  public static final int MINIMUM_PARTICIPANTS = 5;
  public static final String INSUFFICIENT = "Datos insuficientes para garantizar el anonimato";

  private AnalyticsDisclosure() {}

  public static PedagogicalReport disclose(
      CourseDto course,
      AnalyticsFilter filter,
      double threshold,
      List<StructureCount> structures,
      List<AlgorithmCount> algorithms,
      List<RetryCount> retries) {
    boolean allStructuresVisible = structures.stream().allMatch(r -> eligible(r.participants()));
    long total = structures.stream().mapToLong(StructureCount::frequency).sum();
    List<StructureMetric> structureMetrics =
        structures.stream()
            .map(
                r ->
                    eligible(r.participants())
                        ? new StructureMetric(
                            r.structure(),
                            AVAILABLE,
                            r.frequency(),
                            allStructuresVisible && total > 0
                                ? 100.0 * r.frequency() / total
                                : null)
                        : new StructureMetric(r.structure(), SUPPRESSED, null, null))
            .sorted(
                Comparator.comparing(
                        StructureMetric::frequency, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(StructureMetric::structure))
            .toList();
    List<AlgorithmMetric> algorithmMetrics =
        algorithms.stream()
            .map(
                r -> {
                  if (!eligible(r.participants()))
                    return new AlgorithmMetric(r.algorithm(), SUPPRESSED, null, SUPPRESSED, null);
                  // Un promedio parcial se interpretaría como el de todas las ejecuciones: se
                  // declara ausente.
                  Status stepsStatus =
                      r.measuredExecutions() != r.executions()
                          ? UNAVAILABLE
                          : eligible(r.measuredParticipants()) ? AVAILABLE : SUPPRESSED;
                  return new AlgorithmMetric(
                      r.algorithm(),
                      AVAILABLE,
                      r.executions(),
                      stepsStatus,
                      stepsStatus == AVAILABLE ? r.averageSteps() : null);
                })
            .sorted(
                Comparator.comparing(
                        AlgorithmMetric::executions,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AlgorithmMetric::algorithm))
            .toList();
    List<RetryMetric> retryMetrics =
        retries.stream()
            .map(
                r -> {
                  // También se protege cada subgrupo no vacío del cociente, incluido su
                  // complemento.
                  if (!eligible(r.participants())
                      || smallNonempty(r.retryParticipants())
                      || smallNonempty(r.otherParticipants())) {
                    return new RetryMetric(r.structure(), SUPPRESSED, null, null, null, null);
                  }
                  double rate = (double) r.retries() / r.sessions();
                  return new RetryMetric(
                      r.structure(), AVAILABLE, r.sessions(), r.retries(), rate, rate > threshold);
                })
            .sorted(
                Comparator.comparing(
                        RetryMetric::retryRate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RetryMetric::structure))
            .toList();
    return new PedagogicalReport(
        course,
        filter.from(),
        filter.to(),
        AnalyticsFilter.ZONE.getId(),
        MINIMUM_PARTICIPANTS,
        threshold,
        3,
        120,
        allStructuresVisible ? total : null,
        structureMetrics,
        algorithmMetrics,
        retryMetrics);
  }

  private static boolean eligible(long participants) {
    return participants >= MINIMUM_PARTICIPANTS;
  }

  private static boolean smallNonempty(long participants) {
    return participants > 0 && !eligible(participants);
  }
}
