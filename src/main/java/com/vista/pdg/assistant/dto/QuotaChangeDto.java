package com.vista.pdg.assistant.dto;

import com.vista.pdg.assistant.entity.QuotaChange;
import java.time.Instant;

public record QuotaChangeDto(
    String courseCode, Integer previousQuota, int newQuota, String changedBy, Instant changedAt) {
  public static QuotaChangeDto from(QuotaChange q) {
    return new QuotaChangeDto(
        q.getCourse().getCode(),
        q.getPreviousQuota(),
        q.getNewQuota(),
        q.getChangedBy(),
        q.getChangedAt());
  }
}
