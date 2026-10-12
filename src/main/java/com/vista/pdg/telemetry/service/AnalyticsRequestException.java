package com.vista.pdg.telemetry.service;

/** Error del contrato analítico, traducido centralmente a HTTP por GlobalExceptionHandler. */
public class AnalyticsRequestException extends RuntimeException {
  private final int status;
  private final String code;

  public AnalyticsRequestException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static AnalyticsRequestException invalid(String message) {
    return new AnalyticsRequestException(400, "INVALID_ANALYTICS_FILTER", message);
  }

  public static AnalyticsRequestException forbidden() {
    return new AnalyticsRequestException(
        403, "ANALYTICS_FORBIDDEN", "No tienes acceso a este curso");
  }
}
