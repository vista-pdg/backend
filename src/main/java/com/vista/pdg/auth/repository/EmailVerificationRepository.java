package com.vista.pdg.auth.repository;

import com.vista.pdg.auth.entity.EmailVerification;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from EmailVerification v where v.email=:email")
  Optional<EmailVerification> lockEmail(@Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from EmailVerification v where v.requestId=:id")
  Optional<EmailVerification> lockRequest(@Param("id") UUID id);

  @Modifying
  @Query("delete from EmailVerification v where v.expiresAt < :before")
  int deleteStale(@Param("before") Instant before);
}
