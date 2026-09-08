package com.vista.pdg.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.assistant.dto.QuotaStatus;
import com.vista.pdg.assistant.service.AssistantQuotaService;
import com.vista.pdg.assistant.session.AssistantSessionService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.controller.dto.GenerateRequest;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.StructureResponse;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.llm.def.ConversationContext;
import com.vista.pdg.service.llm.def.LlmAdapter;
import com.vista.pdg.telemetry.service.InteractionEvent;
import com.vista.pdg.telemetry.service.TelemetryService;
import com.vista.pdg.telemetry.service.VisualizationMode;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StructureController {

  private final LlmAdapter llmAdapter;
  private final GeneratorDispatcher generatorDispatcher;
  private final LayoutDispatcher layoutDispatcher;
  private final TelemetryService telemetryService;
  private final AssistantQuotaService quotaService;
  private final AssistantSessionService sessionService;
  private final ObjectMapper objectMapper;

  /** Cabecera con la que el cliente sabe si la memoria de sesión está funcionando (HU-32). */
  public static final String MEMORY_HEADER = "X-Assistant-Memory";

  public StructureController(
      LlmAdapter llmAdapter,
      GeneratorDispatcher generatorDispatcher,
      LayoutDispatcher layoutDispatcher,
      TelemetryService telemetryService,
      AssistantQuotaService quotaService,
      AssistantSessionService sessionService,
      ObjectMapper objectMapper) {
    this.llmAdapter = llmAdapter;
    this.generatorDispatcher = generatorDispatcher;
    this.layoutDispatcher = layoutDispatcher;
    this.telemetryService = telemetryService;
    this.quotaService = quotaService;
    this.sessionService = sessionService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/generate")
  public ResponseEntity<StructureResponse> generate(
      @RequestBody GenerateRequest req,
      @RequestHeader(value = VisualizationMode.HEADER, required = false) String mode,
      @AuthenticationPrincipal User user) {
    // HU-17: la reserva va ANTES del modelo. Un 429 nunca produce una llamada facturable.
    QuotaStatus quota = quotaService.reserve(user);

    // HU-32: la sesión de trabajo, si la hay, viaja como contexto. Sin ella (o sin Redis) esto es
    // exactamente la generación de siempre.
    ConversationContext context = sessionService.contextFor(user.getId());
    // HU-21 · CA-4: todos los eventos de esta petición comparten la sesión de trabajo.
    String sessionId = sessionService.ensureSessionId(user.getId());

    StructureContract contract;
    GeneratedStructure structure;
    try {
      contract = llmAdapter.generate(req.prompt(), context);
      structure = generatorDispatcher.dispatch(contract);
    } catch (RuntimeException e) {
      // HU-21 · CA-3: lo que el estudiante pidió y no obtuvo también es un dato. Se distingue lo
      // que la plataforma no cubre (señal pedagógica) de que el proveedor no respondiera.
      telemetryService.recordFailedGeneration(user, req.prompt(), outcomeOf(e), mode, sessionId);
      throw e;
    }

    Map<String, Vec3> positions = layoutDispatcher.compute(structure);
    // HU-16 CA-5: el evento se registra seudonimizado y sólo si la generación tuvo éxito.
    telemetryService.recordGeneration(user, structure, mode, sessionId);
    StructureResponse body = StructureResponse.of(contract, structure, positions);
    boolean remembered = remember(user.getId(), contract, req.prompt(), body);

    return ResponseEntity.ok()
        .header("X-Quota-Limit", String.valueOf(quota.limit()))
        .header("X-Quota-Remaining", String.valueOf(quota.remaining()))
        .header("X-Quota-Reset", quota.resetsAt().toString())
        .header(MEMORY_HEADER, remembered ? "active" : "unavailable")
        .body(body);
  }

  /**
   * Un fallo del proveedor es un problema de operación; todo lo demás —contrato inválido, tipo no
   * soportado, el modelo agotó los intentos— es que el estudiante pidió algo que no cubrimos.
   */
  private static InteractionEvent.Outcome outcomeOf(RuntimeException e) {
    return e instanceof LlmUnavailableException
        ? InteractionEvent.Outcome.ERROR
        : InteractionEvent.Outcome.FUERA_DE_ALCANCE;
  }

  /**
   * Deja la estructura recién generada como contrato vigente de la sesión. Se serializa el contrato
   * —no la estructura ya posicionada— porque es lo que el modelo entiende y lo que puede devolver
   * modificado en el siguiente turno.
   */
  private boolean remember(
      Long userId, StructureContract contract, String prompt, StructureResponse body) {
    try {
      String json = objectMapper.writeValueAsString(contract);
      String summary =
          body.meta() == null
              ? "estructura generada"
              : body.meta().type()
                  + (body.meta().subtype() == null ? "" : "/" + body.meta().subtype())
                  + " con "
                  + body.meta().nodeCount()
                  + " nodos";
      return sessionService.remember(userId, json, contract.type(), prompt, summary);
    } catch (Exception e) {
      return false;
    }
  }
}
