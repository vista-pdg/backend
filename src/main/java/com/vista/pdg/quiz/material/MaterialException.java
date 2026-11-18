package com.vista.pdg.quiz.material;

/** Una fuente que no se pudo leer; el mensaje se muestra tal cual al docente. */
public class MaterialException extends RuntimeException {
  public MaterialException(String message) {
    super(message);
  }

  public MaterialException(String message, Throwable cause) {
    super(message, cause);
  }
}
