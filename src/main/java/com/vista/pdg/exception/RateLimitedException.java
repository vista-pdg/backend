package com.vista.pdg.exception;

/** Demasiados mensajes en la ventana de un minuto. Se responde 429 con {@code Retry-After}. */
public class RateLimitedException extends RuntimeException {

  private final long retryAfterSeconds;

  public RateLimitedException(long retryAfterSeconds) {
    super("Demasiados mensajes seguidos. Podrás enviar de nuevo en " + retryAfterSeconds + " s.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
