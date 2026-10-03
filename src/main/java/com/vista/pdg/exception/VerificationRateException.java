package com.vista.pdg.exception;

import lombok.Getter;

@Getter
public class VerificationRateException extends RuntimeException {
  private final long retryAfterSeconds;

  public VerificationRateException(long seconds) {
    super("Espera " + seconds + " s antes de solicitar otro código.");
    retryAfterSeconds = seconds;
  }
}
