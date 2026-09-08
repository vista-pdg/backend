package com.vista.pdg.assistant.dto;

import com.vista.pdg.academic.entity.Course;

/** Curso con su cuota configurada (nula = por defecto) y la que rige efectivamente. */
public record CourseQuotaDto(
    String code, String name, String termCode, Integer dailyQuota, int effectiveDailyQuota) {
  public static CourseQuotaDto from(Course c, int defaultQuota) {
    return new CourseQuotaDto(
        c.getCode(),
        c.getName(),
        c.getTerm().getCode(),
        c.getDailyQuota(),
        c.getDailyQuota() != null ? c.getDailyQuota() : defaultQuota);
  }
}
