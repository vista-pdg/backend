package com.vista.pdg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acceso a Gemini. Dos vías, la misma API y el mismo SDK:
 *
 * <ul>
 *   <li><b>AI Studio</b> (por defecto): {@code key} de https://aistudio.google.com/apikey. Se cobra
 *       al monedero prepago del proyecto de AI Studio.
 *   <li><b>Vertex AI</b> ({@code vertex=true}): sin clave; usa las credenciales por defecto de
 *       Google Cloud ({@code gcloud auth application-default login}) y se cobra a la cuenta de
 *       facturación del proyecto {@code project}, incluidos los créditos de prueba.
 * </ul>
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(Api api) {
  public record Api(String key, String model, boolean vertex, String project, String location) {}
}
