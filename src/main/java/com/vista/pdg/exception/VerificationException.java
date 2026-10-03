package com.vista.pdg.exception;

import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
  private final String code;

  public VerificationException(String code, String message) {
    super(message);
    this.code = code;
  }
}
