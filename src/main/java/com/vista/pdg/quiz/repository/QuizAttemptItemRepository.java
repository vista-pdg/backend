package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.QuizAttemptItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptItemRepository extends JpaRepository<QuizAttemptItem, Long> {
  List<QuizAttemptItem> findByAttempt_IdOrderByPositionAsc(Long attemptId);

  List<QuizAttemptItem> findByAttempt_Quiz_IdOrderByAttempt_IdAscPositionAsc(Long quizId);

  long countByQuestion_Id(Long questionId);
}
