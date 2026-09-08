package com.vista.pdg.service.algorithm.def;

/**
 * Entrada del catálogo de algoritmos (HU-19 · CA-4). {@code input} dice qué necesita el algoritmo:
 * {@code values} (construye su propia estructura) o {@code structure} (recorre la del lienzo).
 */
public record AlgorithmDescriptor(
    String type,
    String subtype,
    String operation,
    String family,
    String label,
    String description,
    String input) {

  public String key() {
    return type + "/" + subtype + "/" + operation;
  }
}
