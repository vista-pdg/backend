package com.vista.pdg.auth.service;

import com.vista.pdg.auth.entity.VerificationQuota;
import com.vista.pdg.auth.repository.VerificationQuotaRepository;
import com.vista.pdg.exception.VerificationRateException;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class VerificationQuotaService {
  private final VerificationQuotaRepository repository;
  private final Clock clock;

  @Value("${auth.verification.hourly-limit:50}")
  private int hourlyLimit;

  @Value("${auth.verification.daily-limit:200}")
  private int dailyLimit;

  // Reserva durable incluso si falla SMTP o la transacción del desafío se revierte.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void consume() {
    take("hour", Duration.ofHours(1), hourlyLimit);
    take("day", Duration.ofDays(1), dailyLimit);
  }

  private void take(String id, Duration window, int limit) {
    Instant now = clock.instant();
    VerificationQuota q =
        repository
            .lock(id)
            .orElseGet(
                () -> {
                  VerificationQuota n = new VerificationQuota();
                  n.setId(id);
                  n.setWindowStart(now);
                  return n;
                });
    if (!q.getWindowStart().plus(window).isAfter(now)) {
      q.setWindowStart(now);
      q.setRequests(0);
    }
    if (q.getRequests() >= limit)
      throw new VerificationRateException(
          Math.max(1, Duration.between(now, q.getWindowStart().plus(window)).toSeconds() + 1));
    q.setRequests(q.getRequests() + 1);
    repository.saveAndFlush(q);
  }
}
