package com.vista.pdg.exception;

/** Provider failure independent of prompt content; only transient overloads are retryable. */
public class LlmUnavailableException extends RuntimeException {
  private final boolean retryable;

  public LlmUnavailableException(String message, Throwable cause) {
    this(message, cause, false);
  }

  public LlmUnavailableException(String message, Throwable cause, boolean retryable) {
    super(message, cause);
    this.retryable = retryable;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
