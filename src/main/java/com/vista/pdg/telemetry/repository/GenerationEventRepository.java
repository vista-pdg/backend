package com.vista.pdg.telemetry.repository;

import com.vista.pdg.telemetry.entity.GenerationEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
