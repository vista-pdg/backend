package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import com.vista.pdg.telemetry.service.Pseudonymizer;
import com.vista.pdg.telemetry.service.TelemetryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** HU-21: cada punto de captura y lo que deja en la fila. */
class InteractionEventTest extends IntegrationTestSupport {

  private static final String GENERATE = "/api/generate";
  private static final String STEPS = "/api/algorithm/steps";
  private static final String EVENTS = "/api/assistant/events";

  @Autowired private GenerationEventRepository events;
  @Autowired private UserRepository users;
  @Autowired private Pseudonymizer pseudonymizer;

  private List<GenerationEvent> eventsOf(AuthResponse s) {
    Long id = users.findByEmail(s.email()).orElseThrow().getId();
    return events.findByPseudonym(pseudonymizer.pseudonymFor(id));
  }

  private void generate(String token, String prompt) throws Exception {
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + token)
                .header("X-Visualization-Mode", "2D")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-1: una estructura generada deja tipo detallado, origen y resultado")
  void generationEvent() throws Exception {
    AuthResponse s = registerStudent("analitica.generacion");
    generate(s.accessToken(), "crea un grafo no dirigido con 6 nodos");

    GenerationEvent ev = eventsOf(s).getLast();
    assertThat(ev.getStructureType()).isEqualTo("grafo_no_dirigido");
    assertThat(ev.getInteractionSource()).isEqualTo("asistente_nlp");
    assertThat(ev.getOutcome()).isEqualTo("exito");
    assertThat(ev.getKind()).isEqualTo(TelemetryService.KIND_GENERATION);
    assertThat(ev.effectiveSchemaVersion()).isEqualTo(2);
    assertThat(ev.getCourseCode()).isEqualTo("CEDI-G1");
    assertThat(ev.getSessionId()).isNotBlank();
    assertThat(ev.getCreatedAt()).isNotNull();
    assertThat(ev.getPromptText()).as("en los éxitos no se guarda el texto").isNull();
  }

  @Test
  @DisplayName("CA-2: la ejecución de un algoritmo deja su nombre, tipo y número de pasos")
  void algorithmEvent() throws Exception {
    AuthResponse s = registerStudent("analitica.algoritmo");
    mockMvc
        .perform(
            post(STEPS)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"tree\",\"subtype\":\"avl\",\"operation\":\"insert\",\"values\":[10,5,3]}"))
        .andExpect(status().isOk());

    GenerationEvent ev = eventsOf(s).getLast();
    assertThat(ev.getAlgorithm()).as("normalizado a mayúsculas al escribir").isEqualTo("INSERT");
    assertThat(ev.getStructureType()).isEqualTo("arbol_avl");
    assertThat(ev.getInteractionSource()).isEqualTo("catalogo_algoritmos");
    assertThat(ev.getStepCount()).isNotNull().isGreaterThan(1);
    assertThat(ev.getOutcome()).isEqualTo("exito");
  }

  @Test
  @DisplayName("CA-2: el cliente reporta el recorrido completo con los pasos que dio")
  void clientReportsCompletion() throws Exception {
    AuthResponse s = registerStudent("analitica.completado");
    mockMvc
        .perform(
            post(EVENTS)
                .header("Authorization", "Bearer " + s.accessToken())
                .header("X-Visualization-Mode", "3D")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"event\":\"algorithm_completed\",\"type\":\"graph\",\"subtype\":\"simple\","
                        + "\"algorithm\":\"bfs\",\"stepCount\":16,\"nodeCount\":8}"))
        .andExpect(status().isAccepted());

    GenerationEvent ev = eventsOf(s).getLast();
    assertThat(ev.getKind()).isEqualTo(TelemetryService.KIND_ALGORITHM_COMPLETED);
    assertThat(ev.getAlgorithm()).as("el catálogo envía «bfs»; CA-2 pide «BFS»").isEqualTo("BFS");
    assertThat(ev.getStructureType()).isEqualTo("grafo_no_dirigido");
    assertThat(ev.getStepCount()).isEqualTo(16);
    assertThat(ev.getVisualizationMode()).isEqualTo("3D");
    assertThat(ev.getSessionId()).isNotBlank();
  }

  @Test
  @DisplayName("el cliente no puede escribir identidad ni cohorte: las pone el servidor")
  void clientCannotForgeIdentity() throws Exception {
    AuthResponse s = registerStudent("analitica.forjado");
    Long id = users.findByEmail(s.email()).orElseThrow().getId();
    mockMvc
        .perform(
            post(EVENTS)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"event\":\"algorithm_completed\",\"type\":\"stack\",\"algorithm\":\"pop\","
                        + "\"stepCount\":9,\"pseudonym\":\"falsificado\",\"courseCode\":\"OTRO\"}"))
        .andExpect(status().isAccepted());

    GenerationEvent ev = eventsOf(s).getLast();
    assertThat(ev.getPseudonym()).isEqualTo(pseudonymizer.pseudonymFor(id));
    assertThat(ev.getCourseCode()).isEqualTo("CEDI-G1");
  }

  @Test
  @DisplayName("un evento desconocido se ignora sin error y sin dejar rastro")
  void unknownClientEventIsIgnored() throws Exception {
    AuthResponse s = registerStudent("analitica.desconocido");
    mockMvc
        .perform(
            post(EVENTS)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"otra_cosa\"}"))
        .andExpect(status().isAccepted());
    assertThat(eventsOf(s)).isEmpty();
  }

  @Test
  @DisplayName("reportar un evento exige sesión")
  void reportingRequiresSession() throws Exception {
    mockMvc
        .perform(
            post(EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"algorithm_completed\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-4: los eventos de la misma sesión de trabajo comparten id_sesion")
  void eventsShareTheWorkingSession() throws Exception {
    AuthResponse s = registerStudent("analitica.sesion");
    generate(s.accessToken(), "grafo completo de 3 vertices");
    generate(s.accessToken(), "grafo completo de 3 vertices otra vez");

    List<GenerationEvent> mine = eventsOf(s);
    assertThat(mine).hasSize(2);
    assertThat(mine.get(0).getSessionId()).isEqualTo(mine.get(1).getSessionId()).isNotBlank();
  }

  @Test
  @DisplayName("CA-5: la fila cruda no contiene correo ni nombre, y el seudónimo es el del sistema")
  void pseudonymisedAtOrigin() throws Exception {
    AuthResponse s = registerStudent("analitica.privacidad");
    Long id = users.findByEmail(s.email()).orElseThrow().getId();
    generate(s.accessToken(), "grafo completo de 3 vertices");

    GenerationEvent ev = eventsOf(s).getLast();
    assertThat(ev.getPseudonym()).isEqualTo(pseudonymizer.pseudonymFor(id)).doesNotContain("@");
    assertThat(ev.toString()).doesNotContain(s.email());
  }

  @Test
  @DisplayName("CA-3: una petición fuera de alcance deja el resultado y el texto del prompt")
  void outOfScopeKeepsThePrompt() throws Exception {
    AuthResponse s = registerStudent("analitica.fuera");
    String prompt = "quiero un arbol rojinegro fuera de alcance del syllabus";
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}"))
        .andExpect(status().isServiceUnavailable());

    GenerationEvent ev = eventsOf(s).getLast();
    assertThat(ev.getOutcome()).isEqualTo("fuera_de_alcance");
    assertThat(ev.getPromptText()).isEqualTo(prompt);
    assertThat(ev.getStructureType()).isEqualTo("desconocida");
    assertThat(ev.getInteractionSource()).isEqualTo("asistente_nlp");
    assertThat(ev.getSessionId()).isNotBlank();
  }

  @Test
  @DisplayName(
      "un fallo del proveedor se registra como error de operación, no como señal pedagógica")
  void providerFailureIsAnError() throws Exception {
    AuthResponse s = registerStudent("analitica.proveedor");
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"grafo con el proveedor caido\"}"))
        .andExpect(status().isServiceUnavailable());

    assertThat(eventsOf(s).getLast().getOutcome()).isEqualTo("error");
  }

  @Test
  @DisplayName("CA-6: si el evento no se puede escribir, la petición del estudiante sigue bien")
  void telemetryFailureDoesNotBreakTheRequest() throws Exception {
    AuthResponse s = registerStudent("analitica.resiliencia");
    // Un nombre de algoritmo más largo que su columna: la fila se rechaza en la base de datos.
    String tooLong = "x".repeat(120);
    mockMvc
        .perform(
            post(EVENTS)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"event\":\"algorithm_completed\",\"type\":\"stack\",\"algorithm\":\""
                        + tooLong
                        + "\",\"stepCount\":9}"))
        .andExpect(status().isAccepted());
    assertThat(eventsOf(s))
        .as("el evento no se guardó, pero nadie se enteró salvo el log")
        .isEmpty();

    // Y la generación siguiente funciona con normalidad.
    generate(s.accessToken(), "grafo completo de 3 vertices");
    assertThat(eventsOf(s)).hasSize(1);
  }

  @Test
  @DisplayName("CA-4: tres intentos seguidos sobre lo mismo forman una secuencia de reintento")
  void retrySequenceIsDetected() throws Exception {
    AuthResponse s = registerStudent("analitica.reintento");
    for (int i = 0; i < 3; i++)
      generate(s.accessToken(), "grafo completo de 3 vertices, intento " + i);

    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    String sessionId = eventsOf(s).getLast().getSessionId();
    mockMvc
        .perform(get("/api/analytics/retries").header("Authorization", "Bearer " + teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.sessionId=='" + sessionId + "')].attempts").value(3))
        .andExpect(
            jsonPath("$[?(@.sessionId=='" + sessionId + "')].structureType")
                .value("grafo_no_dirigido"));
  }

  @Test
  @DisplayName("dos intentos no son una secuencia, y una ventana corta descarta los lentos")
  void twoAttemptsAreNotARetry() throws Exception {
    AuthResponse s = registerStudent("analitica.dos");
    generate(s.accessToken(), "grafo completo de 3 vertices");
    generate(s.accessToken(), "grafo completo de 3 vertices otra vez");
    String sessionId = eventsOf(s).getLast().getSessionId();
    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();

    mockMvc
        .perform(get("/api/analytics/retries").header("Authorization", "Bearer " + teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.sessionId=='" + sessionId + "')]").isEmpty());

    // Con una ventana de cero segundos ni siquiera los rápidos cuentan.
    mockMvc
        .perform(
            get("/api/analytics/retries?minAttempts=2&windowSeconds=0")
                .header("Authorization", "Bearer " + teacher))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("la analítica del docente sigue siendo sólo suya")
  void retriesAreTeacherOnly() throws Exception {
    String student = registerStudent("analitica.rol").accessToken();
    mockMvc
        .perform(get("/api/analytics/retries").header("Authorization", "Bearer " + student))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/analytics/retries")).andExpect(status().isUnauthorized());

    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    mockMvc
        .perform(get("/api/analytics/retries").header("Authorization", "Bearer " + teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
