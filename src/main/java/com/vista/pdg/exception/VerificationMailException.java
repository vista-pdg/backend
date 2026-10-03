package com.vista.pdg.exception;

public class VerificationMailException extends RuntimeException {
  public VerificationMailException() {
    super("No pudimos enviar el código. Intenta de nuevo más tarde.");
  }
}
