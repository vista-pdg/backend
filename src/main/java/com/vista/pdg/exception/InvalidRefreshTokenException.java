package com.vista.pdg.exception;

/** El token de refresco no existe, expiró o no es utilizable. Se responde 401. */
public class InvalidRefreshTokenException extends RuntimeException {
  public InvalidRefreshTokenException(String message) {
    super(message);
  }
}
