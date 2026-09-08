package com.vista.pdg.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String displayName,
    @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String email,
    @NotBlank(message = "La contraseña es obligatoria") String password,
    @NotBlank(message = "Debes confirmar la contraseña") String confirmPassword,
    @NotBlank(message = "Selecciona el curso al que perteneces") String courseCode) {}
