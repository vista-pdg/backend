package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.StudyQuiz;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyQuizRepository extends JpaRepository<StudyQuiz, Long> {
  List<StudyQuiz> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

  Optional<StudyQuiz> findByIdAndOwner_Id(Long id, Long ownerId);
}
