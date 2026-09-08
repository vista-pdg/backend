package com.vista.pdg.telemetry.service;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.telemetry.dto.AnalyticsSummary;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

  public static final String KIND_GENERATION = "generation";
  public static final String KIND_ALGORITHM = "algorithm";

  private final GenerationEventRepository repository;
  private final Pseudonymizer pseudonymizer;

  /**
   * Registra una generación. Corre en su propia transacción y nunca propaga el fallo: la telemetría
   * es un subproducto y un problema al escribirla no debe negarle al estudiante la estructura que
   * acaba de pedir.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordGeneration(User user, GeneratedStructure structure, String visualizationMode) {
    try {
      var contract = structure.contract();
      repository.save(
          base(user, visualizationMode)
              .kind(KIND_GENERATION)
              .structureType(contract.type())
              .subtype(subtypeOf(structure))
              .nodeCount(structure.nodes().size())
              .build());
    } catch (RuntimeException e) {
      log.error("No se pudo registrar el evento de generación: {}", e.getMessage(), e);
    }
  }

  /**
   * HU-18 · CA-7: una ejecución paso a paso también es un evento, con el modo de visualización en
   * el que el estudiante la pidió. Misma política de fallo que la generación.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordAlgorithm(
      User user, String type, String subtype, int nodeCount, String visualizationMode) {
    try {
      repository.save(
          base(user, visualizationMode)
              .kind(KIND_ALGORITHM)
              .structureType(type)
              .subtype(subtype)
              .nodeCount(nodeCount)
              .build());
    } catch (RuntimeException e) {
      log.error("No se pudo registrar el evento de algoritmo: {}", e.getMessage(), e);
    }
  }

  private GenerationEvent.GenerationEventBuilder base(User user, String visualizationMode) {
    return GenerationEvent.builder()
        .pseudonym(pseudonymizer.pseudonymFor(user.getId()))
        .courseCode(user.getCourse() != null ? user.getCourse().getCode() : null)
        .termCode(user.getCourse() != null ? user.getCourse().getTerm().getCode() : null)
        .visualizationMode(VisualizationMode.normalize(visualizationMode))
        .createdAt(Instant.now());
  }

  @Transactional(readOnly = true)
  public AnalyticsSummary summary() {
    return new AnalyticsSummary(
        repository.countGenerations(),
        repository.countAlgorithmRuns(),
        repository.countDistinctPseudonyms(),
        toMap(repository.countByCourse()),
        toMap(repository.countByStructureType()),
        toMap(repository.countByVisualizationMode()));
  }

  private static Map<String, Long> toMap(List<Object[]> rows) {
    Map<String, Long> out = new LinkedHashMap<>();
    for (Object[] r : rows) out.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
    return out;
  }

  private static String subtypeOf(GeneratedStructure s) {
    Object st = s.computedProperties().get("subtype");
    if (st != null) return String.valueOf(st);
    return switch (s.contract()) {
      case com.vista.pdg.model.contract.TreeContract t -> t.subtype();
      case com.vista.pdg.model.contract.LinkedListContract l -> l.subtype();
      default -> null;
    };
  }
}
