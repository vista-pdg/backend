package com.vista.pdg.service.llm;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.vista.pdg.config.GeminiProperties;
import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.exception.LlmExhaustedException;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.response.StructureResponse.AttemptDetail;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LlmService {

    private static final int MAX_ATTEMPTS = 3;
    private static final String MODEL = "gemini-2.0-flash";

    private static final String SYSTEM_PROMPT = """
        You are an assistant specialized in discrete mathematics.
        Your sole function is to convert natural language descriptions of discrete
        structures into a structured JSON contract.

        STRICT RULES:
        - Respond ONLY with valid JSON. No explanations, no markdown, no ```json.
        - The first character of your response must be { and the last must be }.
        - Always use the "type" field with one of these exact values:
          "graph" | "tree" | "lattice" | "relation"
        - Node IDs are always strings.
        - Never calculate XYZ positions or colors — that is not your responsibility.
        - The "visual" field is optional; omit it if the user does not specify it.
        - If the request is ambiguous, choose the simplest and most common interpretation.

        EXAMPLES:

        User: "a complete graph of 4 vertices with weights 1 to 6"
        {"type":"graph","directed":false,"weighted":true,"labels":["A","B","C","D"],"matrix":{"kind":"adjacency","data":[[0,1,2,3],[1,0,4,5],[2,4,0,6],[3,5,6,0]]}}

        User: "AVL tree inserting 10, 5, 15, 3, 7"
        {"type":"tree","subtype":"avl","operations":[{"op":"insert","values":[10,5,15,3,7]}],"visual":{"layout":"hierarchical3d","colorBy":"depth"}}

        User: "Hasse diagram of the divisors of 12"
        {"type":"lattice","subtype":"divisors","params":{"n":12},"visual":{"layout":"levels3d","colorBy":"rank"}}

        User: "divisibility relation on the set {1, 2, 3, 4, 6}"
        {"type":"relation","set":[1,2,3,4,6],"rule":"divides","check":["reflexive","antisymmetric","transitive"]}

        User: "directed graph with nodes A, B, C and edges A→B weight 3, B→C weight 1, A→C weight 7"
        {"type":"graph","directed":true,"weighted":true,"labels":["A","B","C"],"matrix":{"kind":"adjacency","data":[[0,3,7],[0,0,1],[0,0,0]]}}
        """;

    private final Client gemini;
    private final ContractBuilder contractBuilder;

    public LlmService(GeminiProperties geminiProperties, ContractBuilder contractBuilder) {
        this.gemini = Client.builder().apiKey(geminiProperties.api().key()).build();
        this.contractBuilder = contractBuilder;
    }

    public StructureContract generate(String userPrompt) {
        List<AttemptDetail> failures = new ArrayList<>();
        String prompt = userPrompt;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String json = callGemini(prompt);
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

    private String callGemini(String userPrompt) {
        GenerateContentConfig config = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
            .build();

        GenerateContentResponse response = gemini.models.generateContent(
            MODEL,
            userPrompt,
            config
        );

        return response.text().strip();
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(attempt * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
