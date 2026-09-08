package com.vista.pdg.exception;

import java.time.Instant;

/** La cuota diaria del usuario está agotada. Se responde 429; el modelo no llega a invocarse. */
public class DailyQuotaExceededException extends RuntimeException {

  /** Literal fijado por CA-2 de la HU-17: el E2E lo compara palabra por palabra. */
  public static final String MESSAGE = "Alcanzaste tu límite diario. Se restablece a medianoche.";

  private final Instant resetsAt;

  public DailyQuotaExceededException(Instant resetsAt) {
    super(MESSAGE);
    this.resetsAt = resetsAt;
  }

  public Instant resetsAt() {
    return resetsAt;
  }
}
