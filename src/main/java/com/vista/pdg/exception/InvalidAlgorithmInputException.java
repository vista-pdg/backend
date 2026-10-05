package com.vista.pdg.exception;

/** A rejected educational input, distinct from an internal programming error. */
public class InvalidAlgorithmInputException extends IllegalArgumentException {
  private final String field;

  public InvalidAlgorithmInputException(String message) {
    this(message, null);
  }

  public InvalidAlgorithmInputException(String message, String field) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
