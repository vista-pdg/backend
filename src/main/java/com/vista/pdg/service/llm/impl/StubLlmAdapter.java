package com.vista.pdg.service.llm.impl;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.llm.def.LlmAdapter;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Sustituto determinista de Gemini para el perfil {@code e2e}.
 *
 * <p>Las pruebas de extremo a extremo y la integración continua necesitan que {@code /api/generate}
 * responda siempre igual, sin clave de API, sin red y sin gastar cuota: lo que verifican —que el
 * asistente exija sesión, que la generación quede registrada por cohorte— no depende de lo que el
 * modelo conteste. Fuera de ese perfil este bean no existe, y arrancar con él activo deja una
 * advertencia inconfundible en el log.
 */
@Service
@Primary
@Profile("e2e")
public class StubLlmAdapter implements LlmAdapter {

  private static final Logger log = LoggerFactory.getLogger(StubLlmAdapter.class);

  @PostConstruct
  void warn() {
    log.warn("Perfil e2e activo: /api/generate responde con un grafo fijo, NO llama a Gemini");
  }

  @Override
  public StructureContract generate(String userPrompt) {
    return new GraphContract(
        "graph",
        null,
        false,
        false,
        List.of("A", "B", "C"),
        new GraphContract.MatrixDef(
            "adjacency", List.of(List.of(0, 1, 1), List.of(1, 0, 1), List.of(1, 1, 0)), null));
  }
}
