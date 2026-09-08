package com.vista.pdg.assistant.repository;

import com.vista.pdg.assistant.entity.AssistantUsage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantUsageRepository extends JpaRepository<AssistantUsage, Long> {

  Optional<AssistantUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

  /**
   * Reserva un mensaje de forma atómica: sólo incrementa si aún hay cupo. Dos peticiones
   * simultáneas del mismo usuario en el último mensaje disponible no pueden colarse las dos, porque
   * la condición {@code count < limit} se evalúa en la misma sentencia que el incremento. Devuelve
   * 1 si reservó y 0 si la cuota estaba agotada.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update AssistantUsage u set u.count = u.count + 1, u.updatedAt = :now "
          + "where u.userId = :userId and u.usageDate = :date and u.count < :limit")
  int incrementIfBelow(
      @Param("userId") Long userId,
      @Param("date") LocalDate date,
      @Param("limit") int limit,
      @Param("now") Instant now);
}
