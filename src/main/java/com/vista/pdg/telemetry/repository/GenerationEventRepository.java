package com.vista.pdg.telemetry.repository;

import com.vista.pdg.telemetry.entity.GenerationEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GenerationEventRepository extends JpaRepository<GenerationEvent, Long> {

  List<GenerationEvent> findByPseudonym(String pseudonym);

  @Query(
      "select coalesce(e.courseCode, 'SIN_CURSO'), count(e) from GenerationEvent e "
          + "group by e.courseCode order by 2 desc")
  List<Object[]> countByCourse();

  @Query(
      "select e.structureType, count(e) from GenerationEvent e "
          + "group by e.structureType order by 2 desc")
  List<Object[]> countByStructureType();

  @Query("select count(distinct e.pseudonym) from GenerationEvent e")
  long countDistinctPseudonyms();
}
