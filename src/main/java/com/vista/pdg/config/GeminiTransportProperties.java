package com.vista.pdg.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Límites por instancia, independientes del número de reintentos del SDK. */
@Validated
@ConfigurationProperties("gemini.transport")
public record GeminiTransportProperties(
    @DefaultValue("10") @Min(1) @Max(40) int maxConcurrentRequests,
    @DefaultValue("25000") @Min(1) @Max(55000) int requestTimeoutMs,
    @DefaultValue("5000") @Min(1) @Max(10000) int queueTimeoutMs,
    @DefaultValue("55000") @Min(1) @Max(55000) int totalTimeoutMs) {
  public static GeminiTransportProperties defaults() {
    return new GeminiTransportProperties(10, 25000, 5000, 55000);
  }
}
