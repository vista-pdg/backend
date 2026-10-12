package com.vista.pdg.academic.repository;

import com.vista.pdg.academic.entity.TeachingAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {
  List<TeachingAssignment> findByTeacher_IdOrderByCourse_CodeAsc(Long teacherId);

  List<TeachingAssignment> findByCourse_CodeOrderByTeacher_DisplayNameAsc(String code);

  boolean existsByCourse_CodeAndTeacher_Id(String code, Long teacherId);

  void deleteByCourse_CodeAndTeacher_Id(String code, Long teacherId);
}
