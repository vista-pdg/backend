package com.vista.pdg.quiz.entity;

import java.util.List;

/**
 * Cómo reproducir el escenario de una pregunta en VISTA sin teclearlo (HU-35 · CA-1).
 *
 * <p>{@code chatPrompt} es la instrucción que el chat convierte en la estructura del escenario; es
 * nula cuando el chat no puede construirla igual que el algoritmo (ordenamiento, heapify). {@code
 * values} son los valores que el panel de algoritmos necesita cuando el tema se ejecuta con
 * valores. {@code structureType} y {@code structureSubtype} siguen las claves de selección del
 * cliente ({@code tree/avl}, {@code graph}, {@code hash-table}…). Todo lo validó el servidor antes
 * de redactar la pregunta.
 */
public record VistaSetup(
    String chatPrompt,
    String structureType,
    String structureSubtype,
    String algorithm,
    List<Integer> values,
    Integer argument,
    String start) {

  public VistaSetup {
    values = values == null ? null : List.copyOf(values);
  }
}
