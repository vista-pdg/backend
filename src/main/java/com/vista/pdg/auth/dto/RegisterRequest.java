package com.vista.pdg.auth.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record RegisterRequest(
    @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String displayName,
    @NotBlank(message = "El correo es obligatorio") @Email @Size(max = 254) String email,
    @NotBlank(message = "La contraseña es obligatoria") @Size(max = 72) String password,
    @NotBlank(message = "Debes confirmar la contraseña") @Size(max = 72) String confirmPassword,
    @NotBlank(message = "Selecciona el curso al que perteneces") String courseCode,
    @NotNull(message = "Solicita primero el código de verificación") UUID verificationId,
    @NotBlank(message = "Introduce el código enviado a tu correo")
        @Pattern(regexp = "[0-9]{6}", message = "Introduce los 6 dígitos del código")
        String verificationCode) {
  public RegistrationIntent intent() {
    return new RegistrationIntent(displayName, email, password, confirmPassword, courseCode);
  }

  @Override
  public String toString() {
    return "RegisterRequest[redacted]";
  }
}
