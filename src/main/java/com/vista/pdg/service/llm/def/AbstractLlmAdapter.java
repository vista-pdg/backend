package com.vista.pdg.service.llm.def;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.response.StructureResponse.AttemptDetail;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractLlmAdapter implements LlmAdapter {

    private static final int MAX_ATTEMPTS = 3;

    protected final ContractBuilder contractBuilder;

    protected AbstractLlmAdapter(ContractBuilder contractBuilder) {
        this.contractBuilder = contractBuilder;
    }

    protected abstract String callModel(String systemPrompt, String userPrompt);

    @Override
    public StructureContract generate(String userPrompt) {
        String systemPrompt = loadSystemPrompt();
        List<AttemptDetail> failures = new ArrayList<>();
        String prompt = userPrompt;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String json = callModel(systemPrompt, prompt);
                return contractBuilder.build(json);

            } catch (InvalidContractException e) {
                failures.add(new AttemptDetail(attempt, e.getMessage()));
                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                    prompt = userPrompt + "\n\nYour previous response failed with: " + e.getMessage()
                           + ". Please correct it and respond with valid JSON only.";
                }
            } catch (Exception e) {
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                failures.add(new AttemptDetail(attempt, msg));
                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                    prompt = userPrompt + "\n\nYour previous response failed with: " + msg
                           + ". Please correct it and respond with valid JSON only.";
                }
            }
        }

        throw new LlmExhaustedException(
            "Failed to generate a valid contract after " + MAX_ATTEMPTS + " attempts", failures);
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(systemPromptPath());
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load system prompt from: " + systemPromptPath(), e);
        }
    }

    protected String systemPromptPath() {
        return "prompts/graph/system-prompt.txt";
    }

    // ── Backoff ───────────────────────────────────────────────────────────

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(attempt * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
