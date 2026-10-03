package com.vista.pdg.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "El token de refresco es obligatorio") String refreshToken) {
  @Override
  public String toString() {
    return "RefreshRequest[redacted]";
  }
}
