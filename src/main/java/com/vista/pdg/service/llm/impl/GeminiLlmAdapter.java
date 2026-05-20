package com.vista.pdg.service.llm.impl;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.vista.pdg.config.GeminiProperties;
import com.vista.pdg.service.llm.def.AbstractLlmAdapter;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import org.springframework.stereotype.Service;

@Service
public class GeminiLlmAdapter extends AbstractLlmAdapter {

    private final String model;
    private final Client client;

    public GeminiLlmAdapter(GeminiProperties geminiProperties, ContractBuilder contractBuilder) {
        super(contractBuilder);
        this.client = Client.builder().apiKey(geminiProperties.api().key()).build();
        this.model = geminiProperties.api().model();
    }

    @Override
    protected String callModel(String systemPrompt, String userPrompt) {
        GenerateContentConfig config = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .build();

        GenerateContentResponse response = client.models.generateContent(model, userPrompt, config);
        return response.text().strip();
    }
}
