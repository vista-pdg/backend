package com.vista.pdg.exception;

import com.vista.pdg.model.response.StructureResponse.AttemptDetail;
import java.util.List;

public class LlmExhaustedException extends RuntimeException {
  private final List<AttemptDetail> attempts;

  public LlmExhaustedException(String message, List<AttemptDetail> attempts) {
    super(message);
    this.attempts = attempts;
  }

  public List<AttemptDetail> attempts() {
    return attempts;
  }
}
