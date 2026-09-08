package com.vista.pdg.academic.repository;

import com.vista.pdg.academic.entity.AcademicTerm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
  Optional<AcademicTerm> findByCode(String code);

  Optional<AcademicTerm> findFirstByActiveTrue();
}
