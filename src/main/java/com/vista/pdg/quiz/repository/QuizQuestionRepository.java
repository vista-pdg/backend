package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.QuizQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
  List<QuizQuestion> findByQuiz_IdOrderByIdAsc(Long quizId);

  long countByQuiz_Id(Long quizId);
}
