package com.vista.pdg.auth.dto;

import java.util.List;

/**
 * Respuesta de registro, login y refresco.
 *
 * <p>{@code expiresIn} son segundos de vida del token de acceso: permite al frontend programar el
 * refresco silencioso sin tener que decodificar el JWT.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    String email,
    String displayName,
    List<String> roles,
    /** Nulos cuando la cuenta no está vinculada a un curso (docente, administrador). */
    String courseCode,
    String termCode) {}
