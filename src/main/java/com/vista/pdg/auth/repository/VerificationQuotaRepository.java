package com.vista.pdg.auth.repository;

import com.vista.pdg.auth.entity.VerificationQuota;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VerificationQuotaRepository extends JpaRepository<VerificationQuota, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from VerificationQuota q where q.id=:id")
  Optional<VerificationQuota> lock(@Param("id") String id);
}
