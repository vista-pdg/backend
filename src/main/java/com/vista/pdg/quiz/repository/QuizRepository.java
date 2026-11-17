package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.Quiz;
import com.vista.pdg.quiz.entity.QuizStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
  List<Quiz> findByCourse_CodeInOrderByCreatedAtDesc(Collection<String> courseCodes);

  List<Quiz> findByCourse_IdAndStatusOrderByOpensAtAsc(Long courseId, QuizStatus status);
}
