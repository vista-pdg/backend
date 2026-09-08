package com.vista.pdg.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo único que el cliente puede reportar (HU-21 · CA-2): que terminó de recorrer un algoritmo y
 * cuántos pasos recorrió, que es un hecho que sólo él conoce.
 *
 * <p>Deliberadamente no lleva usuario, curso, sesión ni marca de tiempo: <b>eso lo pone el
 * servidor</b>. Un cliente no puede escribir identidad ni falsear la cohorte de un evento.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientEventRequest(
    String event,
    String type,
    String subtype,
    String algorithm,
    Integer stepCount,
    Integer nodeCount) {

  public static final String ALGORITHM_COMPLETED = "algorithm_completed";
}
