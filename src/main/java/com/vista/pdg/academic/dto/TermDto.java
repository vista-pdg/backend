package com.vista.pdg.academic.dto;

import com.vista.pdg.academic.entity.AcademicTerm;

public record TermDto(String code, boolean active) {
  public static TermDto from(AcademicTerm term) {
    return new TermDto(term.getCode(), term.isActive());
  }
}
