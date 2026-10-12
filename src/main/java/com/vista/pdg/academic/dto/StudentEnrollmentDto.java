package com.vista.pdg.academic.dto;

import com.vista.pdg.auth.entity.User;

public record StudentEnrollmentDto(Long id, String displayName, String email, String courseCode) {
  public static StudentEnrollmentDto from(User user) {
    return new StudentEnrollmentDto(
        user.getId(),
        user.getDisplayName(),
        user.getEmail(),
        user.getCourse() == null ? null : user.getCourse().getCode());
  }
}
