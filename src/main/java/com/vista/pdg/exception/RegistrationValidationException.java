package com.vista.pdg.exception;

/**
 * Un dato del registro no cumple las reglas de negocio que Bean Validation no puede expresar por sí
 * sola (dominio de correo permitido, confirmación de contraseña). Se responde 422 indicando el
 * campo culpable para que el formulario lo señale.
 */
public class RegistrationValidationException extends RuntimeException {

  private final String field;

  public RegistrationValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
