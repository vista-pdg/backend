package com.vista.pdg.auth.dto;

import jakarta.validation.constraints.*;

public record RegistrationIntent(
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 72) String password,
    @NotBlank @Size(max = 72) String confirmPassword,
    @NotBlank String courseCode) {
  @Override
  public String toString() {
    return "RegistrationIntent[redacted]";
  }
}
