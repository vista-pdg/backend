package com.vista.pdg.service.llm.impl;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.vista.pdg.config.GeminiProperties;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.service.llm.def.AbstractLlmAdapter;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GeminiLlmAdapter extends AbstractLlmAdapter {

  private static final Logger log = LoggerFactory.getLogger(GeminiLlmAdapter.class);

  private final String model;
  private final Client client;

  public GeminiLlmAdapter(GeminiProperties geminiProperties, ContractBuilder contractBuilder) {
    super(contractBuilder);
    String key = geminiProperties.api().key();
    this.model = geminiProperties.api().model();
    log.info(
        "GeminiLlmAdapter init — model: {}, key: {}***{}",
        model,
        key.length() > 8 ? key.substring(0, 4) : "????",
        key.length() > 8 ? key.substring(key.length() - 4) : "????");
    this.client = Client.builder().apiKey(key).build();
  }

  @Override
  protected String callModel(String systemPrompt, String userPrompt) {
    log.debug("Calling Gemini model '{}' — prompt: {}", model, userPrompt);
    try {
      // Salida JSON forzada y temperatura 0: el modelo no puede envolver la respuesta en prosa ni
      // en ``` y elige siempre la forma más probable. Lo que siga variando lo absorbe el
      // ContractNormalizer antes de validar.
      GenerateContentConfig config =
          GenerateContentConfig.builder()
              .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
              .responseMimeType("application/json")
              .temperature(0.0f)
              .candidateCount(1)
              .build();
      GenerateContentResponse response = client.models.generateContent(model, userPrompt, config);
      String text = response.text();
      if (text == null || text.isBlank()) {
        throw new IllegalStateException("Gemini returned an empty response");
      }
      log.debug("Gemini raw response: {}", text);
      return text.strip();
    } catch (LlmUnavailableException e) {
      throw e;
    } catch (Exception e) {
      String reason = unavailableReason(e);
      if (reason != null) {
        log.error("Gemini unavailable — model: {}, reason: {}", model, e.getMessage());
        throw new LlmUnavailableException(reason, e);
      }
      log.error("Gemini call failed — model: {}, error: {}", model, e.getMessage(), e);
      throw e;
    }
  }

  /**
   * Distingue los fallos que no dependen del prompt. Se inspecciona el mensaje porque el SDK
   * envuelve el código HTTP en el texto de la excepción.
   */
  public static String unavailableReason(Exception e) {
    String m = e.getMessage() == null ? "" : e.getMessage();
    String lower = m.toLowerCase();
    if (lower.contains("429")
        || lower.contains("resource_exhausted")
        || lower.contains("quota")
        || lower.contains("credits")) {
      return "El proveedor del modelo rechazó la petición por cuota o créditos agotados (429). "
          + "Revisa la facturación de la cuenta de Gemini; el asistente volverá a funcionar sin cambios en la app.";
    }
    if (lower.contains("401")
        || lower.contains("403")
        || lower.contains("api key")
        || lower.contains("api_key_invalid")
        || lower.contains("permission_denied")) {
      return "El proveedor del modelo rechazó la clave de API (401/403). Revisa GEMINI_API_KEY en backend/.env.";
    }
    if (lower.contains("404") || lower.contains("not_found") || lower.contains("is not found")) {
      return "El modelo configurado no existe para esta clave (404). Revisa GEMINI_API_MODEL en backend/.env.";
    }
    if (lower.contains("503") || lower.contains("unavailable") || lower.contains("overloaded")) {
      return "El servicio del modelo no está disponible en este momento (503). Inténtalo de nuevo en unos segundos.";
    }
    return null;
  }
}
