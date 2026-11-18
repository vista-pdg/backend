package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.QuizMaterial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizMaterialRepository extends JpaRepository<QuizMaterial, Long> {
  List<QuizMaterial> findByQuiz_IdOrderByIdAsc(Long quizId);

  long countByQuiz_Id(Long quizId);
}
