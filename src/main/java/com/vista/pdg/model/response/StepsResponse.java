package com.vista.pdg.model.response;

import java.util.List;

/**
 * Rastro de un algoritmo. {@code code} y {@code language} (HU-22a) llevan el pseudocódigo cuyas
 * líneas referencian los pasos; nulos en los algoritmos no instrumentados.
 */
public record StepsResponse(
    boolean error, String message, List<AlgorithmStep> steps, List<String> code, String language) {
  public static StepsResponse ok(List<AlgorithmStep> steps) {
    return new StepsResponse(false, null, steps, null, null);
  }

  public static StepsResponse ok(List<AlgorithmStep> steps, List<String> code, String language) {
    return new StepsResponse(false, null, steps, code, language);
  }

  public static StepsResponse error(String message) {
    return new StepsResponse(true, message, null, null, null);
  }
}
