package com.vista.pdg.telemetry.service;

import com.vista.pdg.auth.entity.User;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.telemetry.dto.AnalyticsSummary;
import com.vista.pdg.telemetry.dto.EventView;
import com.vista.pdg.telemetry.dto.RetrySequence;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class TelemetryService {

  public TelemetryService(
      GenerationEventRepository repository,
      Pseudonymizer pseudonymizer,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.pseudonymizer = pseudonymizer;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public static final String KIND_GENERATION = "generation";
  public static final String KIND_ALGORITHM = "algorithm";

  /** HU-21 · CA-2: el estudiante recorrió el rastro hasta el final; lo reporta el cliente. */
  public static final String KIND_ALGORITHM_COMPLETED = "algorithm_completed";

  private final GenerationEventRepository repository;
  private final Pseudonymizer pseudonymizer;

  /**
   * La escritura del evento va en su propia transacción y el {@code try} la envuelve <b>por
   * fuera</b>. Con {@code @Transactional} en este método el {@code catch} quedaría dentro del
   * límite transaccional: una fila rechazada marca la transacción como «sólo rollback» y el error
   * aparece al confirmar, ya fuera del alcance del catch, tumbando la petición del estudiante. Eso
   * es justo lo que CA-6 prohíbe.
   */
  private final TransactionTemplate transaction;

  /** Longitud máxima del prompt que se conserva para revisión docente (HU-21 · CA-3). */
  static final int MAX_PROMPT = 500;

  /**
   * Registra una interacción (HU-21). Corre en su propia transacción y <b>nunca</b> propaga el
   * fallo: la telemetría es un subproducto y un problema al escribirla no debe negarle al
   * estudiante la estructura que acaba de pedir (CA-6).
   */
  public void record(InteractionEvent event) {
    try {
      User user = event.user();
      transaction.executeWithoutResult(
          status ->
              repository.save(
                  GenerationEvent.builder()
                      .pseudonym(pseudonymizer.pseudonymFor(user.getId()))
                      .courseCode(user.getCourse() != null ? user.getCourse().getCode() : null)
                      .termCode(
                          user.getCourse() != null ? user.getCourse().getTerm().getCode() : null)
                      .visualizationMode(VisualizationMode.normalize(event.visualizationMode()))
                      .kind(event.kind())
                      .structureType(event.structureKind())
                      .subtype(event.subtype())
                      .algorithm(event.algorithm())
                      .interactionSource(event.source() == null ? null : event.source().value())
                      .outcome(event.outcome() == null ? null : event.outcome().value())
                      .sessionId(event.sessionId())
                      .stepCount(event.stepCount())
                      .promptText(promptToKeep(event))
                      .nodeCount(event.nodeCount())
                      .schemaVersion(2)
                      .createdAt(Instant.now())
                      .build()));
    } catch (RuntimeException e) {
      log.error("No se pudo registrar el evento de interacción: {}", e.getMessage(), e);
    }
  }

  /** El texto sólo se conserva si algo salió mal, y recortado. */
  private static String promptToKeep(InteractionEvent event) {
    if (event.outcome() == null || event.outcome() == InteractionEvent.Outcome.EXITO) return null;
    String text = event.promptText();
    if (text == null || text.isBlank()) return null;
    return text.length() <= MAX_PROMPT ? text : text.substring(0, MAX_PROMPT);
  }

  /** Una generación con éxito, con el tipo detallado que pide CA-1. */
  public void recordGeneration(
      User user, GeneratedStructure structure, String visualizationMode, String sessionId) {
    record(
        InteractionEvent.of(user, KIND_GENERATION)
            .structureKind(StructureKind.of(structure.contract()))
            .subtype(subtypeOf(structure))
            .nodeCount(structure.nodes().size())
            .source(InteractionEvent.Source.ASISTENTE_NLP)
            .outcome(InteractionEvent.Outcome.EXITO)
            .visualizationMode(visualizationMode)
            .sessionId(sessionId)
            .build());
  }

  /** Una generación que no llegó a estructura: qué pidió el estudiante y por qué falló (CA-3). */
  public void recordFailedGeneration(
      User user,
      String prompt,
      InteractionEvent.Outcome outcome,
      String visualizationMode,
      String sessionId) {
    record(
        InteractionEvent.of(user, KIND_GENERATION)
            .structureKind(StructureKind.DESCONOCIDA)
            .source(InteractionEvent.Source.ASISTENTE_NLP)
            .outcome(outcome)
            .visualizationMode(visualizationMode)
            .sessionId(sessionId)
            .promptText(prompt)
            .build());
  }

  /** HU-18 · CA-7 y HU-21 · CA-2: una ejecución paso a paso, con su algoritmo y sus pasos. */
  public void recordAlgorithm(
      User user,
      String type,
      String subtype,
      String algorithm,
      int nodeCount,
      Integer stepCount,
      String visualizationMode,
      String sessionId) {
    record(
        InteractionEvent.of(user, KIND_ALGORITHM)
            .structureKind(StructureKind.of(type, subtype))
            .subtype(subtype)
            .algorithm(algorithm)
            .nodeCount(nodeCount)
            .stepCount(stepCount)
            .source(InteractionEvent.Source.CATALOGO_ALGORITMOS)
            .outcome(InteractionEvent.Outcome.EXITO)
            .visualizationMode(visualizationMode)
            .sessionId(sessionId)
            .build());
  }

  /**
   * Secuencias de reintento (HU-21 · CA-4): tres o más eventos de la misma sesión sobre el mismo
   * tipo de estructura dentro de una ventana corta. No se marca al escribir —el tercer intento aún
   * no ha ocurrido— sino que se detecta al consultar, así el umbral se puede cambiar sin migrar.
   */
  @Transactional(readOnly = true)
  public List<RetrySequence> retrySequences(int minAttempts, Duration window) {
    List<RetrySequence> out = new ArrayList<>();
    for (Object[] row : repository.groupBySessionAndStructure(minAttempts)) {
      String sessionId = String.valueOf(row[0]);
      String structureType = String.valueOf(row[1]);
      long attempts = ((Number) row[2]).longValue();
      Instant first = (Instant) row[3];
      Instant last = (Instant) row[4];
      if (Duration.between(first, last).compareTo(window) <= 0) {
        out.add(new RetrySequence(sessionId, structureType, attempts, iso(first), iso(last)));
      }
    }
    return out;
  }

  /**
   * Últimos eventos para revisión docente (HU-21 · CA-3). Devuelve una vista sin seudónimo: se
   * revisa qué se pidió, no quién lo pidió.
   */
  @Transactional(readOnly = true)
  public List<EventView> recentEvents(String outcome, int limit) {
    return repository.findRecent(outcome, PageRequest.of(0, Math.clamp(limit, 1, 200))).stream()
        .map(
            e ->
                new EventView(
                    e.getStructureType(),
                    e.getAlgorithm(),
                    e.getInteractionSource(),
                    e.getOutcome(),
                    e.getStepCount(),
                    e.getVisualizationMode(),
                    e.getSessionId(),
                    e.getPromptText(),
                    iso(e.getCreatedAt())))
        .toList();
  }

  private static String iso(Instant at) {
    return at == null ? null : at.toString();
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
