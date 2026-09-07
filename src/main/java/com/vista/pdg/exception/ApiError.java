package com.vista.pdg.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Cuerpo de error de los endpoints de autenticación.
 *
 * <p>{@code fieldErrors} mapea nombre de campo a mensaje para que el formulario pueda señalar el
 * campo culpable en lugar de mostrar un banner genérico. Se omite cuando el error no es de campo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }

  public static ApiError field(String code, String message, Map<String, String> fieldErrors) {
    return new ApiError(code, message, fieldErrors);
  }
}
