package com.vista.pdg.assistant.repository;

import com.vista.pdg.assistant.entity.QuotaChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaChangeRepository extends JpaRepository<QuotaChange, Long> {
  List<QuotaChange> findByCourse_CodeOrderByChangedAtDesc(String courseCode);
}
