package com.vista.pdg.quiz.repository;

import com.vista.pdg.quiz.entity.StudyQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyQuestionRepository extends JpaRepository<StudyQuestion, Long> {
  List<StudyQuestion> findByStudyQuiz_IdOrderByPositionAsc(Long studyQuizId);

  List<StudyQuestion> findByStudyQuiz_IdInOrderByPositionAsc(List<Long> studyQuizIds);

  Optional<StudyQuestion> findByStudyQuiz_IdAndPosition(Long studyQuizId, int position);
}
