package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.QuizAttempt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
  Optional<QuizAttempt> findByQuiz_IdAndStudent_Id(Long quizId, Long studentId);

  List<QuizAttempt> findByQuiz_Id(Long quizId);

  List<QuizAttempt> findByStudent_IdAndQuiz_IdIn(Long studentId, Collection<Long> quizIds);

  long countByQuiz_Id(Long quizId);

  /**
   * Suma un incidente en la base, no en memoria: el modo de prueba envía varios a la vez (copiar y
   * perder el foco en el mismo instante) y una lectura-modificación-escritura perdería alguno.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update QuizAttempt a set a.integrityEvents = a.integrityEvents + 1 where a.id = :id")
  int incrementIntegrityEvents(@Param("id") Long id);

  @Query("select a.integrityEvents from QuizAttempt a where a.id = :id")
  int integrityEvents(@Param("id") Long id);
}
