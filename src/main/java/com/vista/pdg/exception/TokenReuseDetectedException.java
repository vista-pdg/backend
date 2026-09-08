package com.vista.pdg.exception;

/**
 * Se presentó un token de refresco ya rotado o revocado. La familia completa queda revocada antes
 * de lanzarse esta excepción. Se responde 401 para forzar reautenticación.
 */
public class TokenReuseDetectedException extends RuntimeException {
  public TokenReuseDetectedException(String message) {
    super(message);
  }
}
