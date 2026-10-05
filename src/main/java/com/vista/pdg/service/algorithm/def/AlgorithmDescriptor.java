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
    String input,
    String parameter,
    Integer maxValues) {

  public AlgorithmDescriptor(
      String type,
      String subtype,
      String operation,
      String family,
      String label,
      String description,
      String input,
      String parameter) {
    this(
        type,
        subtype,
        operation,
        family,
        label,
        description,
        input,
        parameter,
        "values".equals(input) ? 64 : null);
  }

  public AlgorithmDescriptor(
      String type,
      String subtype,
      String operation,
      String family,
      String label,
      String description,
      String input) {
    this(type, subtype, operation, family, label, description, input, null);
  }

  public String key() {
    return type + "/" + subtype + "/" + operation;
  }
}
