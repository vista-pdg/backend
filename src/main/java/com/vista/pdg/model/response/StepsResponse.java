package com.vista.pdg.model.response;

import java.util.List;

public record StepsResponse(boolean error, String message, List<AlgorithmStep> steps) {
  public static StepsResponse ok(List<AlgorithmStep> steps) {
    return new StepsResponse(false, null, steps);
  }

  public static StepsResponse error(String message) {
    return new StepsResponse(true, message, null);
  }
}
