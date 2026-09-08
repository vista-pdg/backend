package com.vista.pdg.exception;

/**
 * El proveedor del modelo no puede atender la petición por una causa ajena al contenido: créditos
 * agotados, cuota excedida, clave inválida o servicio caído. Reintentar con el mismo prompt no la
 * arregla, así que el adaptador la lanza sin agotar los intentos y el cliente recibe un 503 con un
 * mensaje que dice qué revisar.
 */
public class LlmUnavailableException extends RuntimeException {
  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
