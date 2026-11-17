package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.QuizAttempt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
  Optional<QuizAttempt> findByQuiz_IdAndStudent_Id(Long quizId, Long studentId);

  List<QuizAttempt> findByQuiz_Id(Long quizId);

  List<QuizAttempt> findByStudent_IdAndQuiz_IdIn(Long studentId, Collection<Long> quizIds);

  long countByQuiz_Id(Long quizId);
}
