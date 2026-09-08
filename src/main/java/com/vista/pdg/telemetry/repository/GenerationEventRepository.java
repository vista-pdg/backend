package com.vista.pdg.telemetry.repository;

import com.vista.pdg.telemetry.entity.GenerationEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationEventRepository extends JpaRepository<GenerationEvent, Long> {

  List<GenerationEvent> findByPseudonym(String pseudonym);

  /** Las filas anteriores a la HU-18 no tienen {@code kind}: eran todas generaciones. */
  String GENERATION = "coalesce(e.kind, 'generation') = 'generation'";

  @Query("select count(e) from GenerationEvent e where " + GENERATION)
  long countGenerations();

  @Query("select count(e) from GenerationEvent e where e.kind = 'algorithm'")
  long countAlgorithmRuns();

  @Query(
      "select coalesce(e.courseCode, 'SIN_CURSO'), count(e) from GenerationEvent e where "
          + GENERATION
          + " group by e.courseCode order by 2 desc")
  List<Object[]> countByCourse();

  @Query(
      "select e.structureType, count(e) from GenerationEvent e where "
          + GENERATION
          + " group by e.structureType order by 2 desc")
  List<Object[]> countByStructureType();

  /** HU-18 · CA-7. Sólo cuenta los eventos que informaron el modo. */
  @Query(
      "select e.visualizationMode, count(e) from GenerationEvent e "
          + "where e.visualizationMode is not null group by e.visualizationMode order by 1")
  List<Object[]> countByVisualizationMode();

  @Query("select count(distinct e.pseudonym) from GenerationEvent e")
  long countDistinctPseudonyms();

  /**
   * HU-21 · CA-4: candidatos a secuencia de reintento — misma sesión y mismo tipo de estructura con
   * al menos {@code minAttempts} eventos. La ventana temporal se filtra fuera, con las marcas que
   * devuelve esta consulta: en JPQL sería una comparación entre agregados difícil de leer.
   */
  @Query(
      "select e.sessionId, e.structureType, count(e), min(e.createdAt), max(e.createdAt) "
          + "from GenerationEvent e where e.sessionId is not null "
          + "group by e.sessionId, e.structureType having count(e) >= :minAttempts "
          + "order by count(e) desc")
  List<Object[]> groupBySessionAndStructure(@Param("minAttempts") long minAttempts);

  List<GenerationEvent> findBySessionIdOrderByCreatedAtAsc(String sessionId);

  /**
   * HU-21: los últimos eventos, para revisión docente. Sin filtro cuando {@code outcome} es nulo.
   */
  @Query(
      "select e from GenerationEvent e where (:outcome is null or e.outcome = :outcome) "
          + "order by e.createdAt desc")
  List<GenerationEvent> findRecent(@Param("outcome") String outcome, Pageable pageable);
}
