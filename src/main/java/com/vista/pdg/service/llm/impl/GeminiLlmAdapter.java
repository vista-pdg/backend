package com.vista.pdg.service.llm.impl;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.vista.pdg.config.GeminiProperties;
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
      GenerateContentConfig config =
          GenerateContentConfig.builder()
              .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
              .build();
      GenerateContentResponse response = client.models.generateContent(model, userPrompt, config);
      String text = response.text().strip();
      log.debug("Gemini raw response: {}", text);
      return text;
    } catch (Exception e) {
      log.error("Gemini call failed — model: {}, error: {}", model, e.getMessage(), e);
      throw e;
    }
  }
}
