package com.vista.pdg.assistant.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Sesión de trabajo del asistente (HU-32): lo que el estudiante tiene «en la mesa».
 *
 * <p>Guarda el contrato vigente —el JSON que el modelo devolvió la última vez— y los últimos turnos
 * para poder refinar («ahora inserta el 7») sin repetir la descripción. <b>No guarda identidad</b>:
 * la clave de Redis es el id de cuenta y dentro no hay correo ni nombre, igual que la telemetría de
 * HU-16. Es memoria corta: caduca sola por TTL y nunca llega a Postgres.
 *
 * <p>{@code updatedAt} es una marca ISO-8601 en texto, no un {@code Instant}: lo que se guarda en
 * Redis debe poder leerse con {@code redis-cli} y deserializarse sin depender de qué módulos de
 * Jackson tenga registrados la aplicación.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantSession(
    String contract, String structureType, List<Turn> turns, String updatedAt) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Turn(String role, String text) {}

  public AssistantSession {
    turns = turns == null ? List.of() : List.copyOf(turns);
  }

  public static AssistantSession empty() {
    return new AssistantSession(null, null, List.of(), null);
  }

  public boolean hasStructure() {
    return contract != null && !contract.isBlank();
  }

  /** Los turnos en el formato que ve el modelo: «usuario: …» / «asistente: …». */
  public List<String> asLines() {
    return turns.stream().map(t -> t.role() + ": " + t.text()).toList();
  }
}
