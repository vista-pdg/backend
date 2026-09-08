package com.vista.pdg.exception;

/** Ya existe una cuenta con ese correo. Se responde 409. */
public class EmailAlreadyUsedException extends RuntimeException {
  public EmailAlreadyUsedException(String message) {
    super(message);
  }
}
