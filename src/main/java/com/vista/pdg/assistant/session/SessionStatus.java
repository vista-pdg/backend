package com.vista.pdg.assistant.session;

/**
 * Estado de la sesión para el cliente (HU-32). {@code available} distingue «no hay sesión» de «la
 * memoria no está disponible»: sin Redis el asistente sigue generando, pero el frontend debe decir
 * por qué dejó de recordar en vez de callarlo.
 */
public record SessionStatus(
    boolean available, boolean active, String structureType, long secondsRemaining) {

  public static SessionStatus unavailable() {
    return new SessionStatus(false, false, null, 0);
  }

  public static SessionStatus idle() {
    return new SessionStatus(true, false, null, 0);
  }
}
