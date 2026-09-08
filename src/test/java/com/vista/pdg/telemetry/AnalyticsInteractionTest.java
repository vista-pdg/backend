package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.dto.EventView;
import com.vista.pdg.telemetry.dto.RetrySequence;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import com.vista.pdg.telemetry.service.Pseudonymizer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * HU-21 — el registro analítico visto de extremo a extremo por la API.
 *
 * <p>Las pruebas unitarias de {@code StructureKind} y {@code InteractionEvent} demuestran que el
 * evento se <em>arma</em> bien; esto demuestra que además se <em>escribe</em> cuando el estudiante
 * hace lo que dice cada escenario, y que el docente lo puede leer. Las dos mitades hacen falta: un
 * mapeo correcto que nadie invoca no registra nada.
 */
class AnalyticsInteractionTest extends IntegrationTestSupport {

  private static final String GENERATE = "/api/generate";
  private static final String STEPS = "/api/algorithm/steps";
  private static final String CLIENT_EVENTS = "/api/assistant/events";

  @Autowired private GenerationEventRepository events;
  @Autowired private UserRepository users;
  @Autowired private Pseudonymizer pseudonymizer;

  private String studentToken() throws Exception {
    return login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
  }

  private List<GenerationEvent> minesOf(String email) {
    User u = users.findByEmail(email).orElseThrow();
    return events.findByPseudonym(pseudonymizer.pseudonymFor(u.getId()));
  }

  private void generate(String token, String prompt, int expectedStatus) throws Exception {
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PromptBody(prompt))))
        .andExpect(status().is(expectedStatus));
  }

  private record PromptBody(String prompt) {}

  /**
   * Registra con un correo que la prueba conoce. {@code registerStudent} lo genera por dentro, y
   * aquí hace falta saberlo para localizar después los eventos de esa cuenta.
   */
  private AuthResponse registerAs(String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new RegisterRequest(
                                "Prueba analítica", email, "clave12345", "clave12345", "CEDI-G1"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, AuthResponse.class);
  }

  // ── CA-1 · Estructura generada por lenguaje natural ───────────────────────

  @Test
  @DisplayName(
      "CA-1: generar por chat deja un evento con tipo detallado, origen asistente_nlp y éxito")
  void generacionPorChatRegistraExito() throws Exception {
    String token = studentToken();

    generate(token, "crea un grafo no dirigido con 3 nodos", 200);

    GenerationEvent ev = minesOf(STUDENT_EMAIL).getLast();
    assertThat(ev.getStructureType()).isEqualTo("grafo_no_dirigido");
    assertThat(ev.getInteractionSource()).isEqualTo("asistente_nlp");
    assertThat(ev.getOutcome()).isEqualTo("exito");
    assertThat(ev.effectiveSchemaVersion()).isEqualTo(2);
    assertThat(ev.getSessionId()).as("agrupa los intentos de una misma sesión").isNotBlank();
    assertThat(ev.getPromptText())
        .as("en los éxitos no se guarda el texto del estudiante")
        .isNull();
  }

  // ── CA-2 · Ejecución de un algoritmo ─────────────────────────────────────

  @Test
  @DisplayName("CA-2: pedir los pasos de un recorrido registra el algoritmo y cuántos pasos son")
  void recorridoRegistraAlgoritmoYPasos() throws Exception {
    String token = studentToken();

    String body =
        """
        {"type":"graph","subtype":"simple","operation":"bfs",\
        "nodes":[{"id":"a","label":"A","x":0,"y":0,"z":0,"depth":0},\
        {"id":"b","label":"B","x":1,"y":0,"z":0,"depth":0},\
        {"id":"c","label":"C","x":2,"y":0,"z":0,"depth":0}],\
        "edges":[{"id":"e1","source":"a","target":"b","directed":false},\
        {"id":"e2","source":"b","target":"c","directed":false}],\
        "start":"a"}
        """;
    mockMvc
        .perform(
            post(STEPS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    GenerationEvent ev = minesOf(STUDENT_EMAIL).getLast();
    assertThat(ev.getAlgorithm()).isEqualTo("BFS");
    assertThat(ev.getStructureType()).isEqualTo("grafo_no_dirigido");
    assertThat(ev.getInteractionSource()).isEqualTo("catalogo_algoritmos");
    assertThat(ev.getStepCount()).as("el número de pasos ejecutados").isNotNull().isPositive();
  }

  @Test
  @DisplayName("CA-2: llegar al último paso deja un evento del cliente con los pasos recorridos")
  void finDelRecorridoLoReportaElCliente() throws Exception {
    String token = studentToken();

    mockMvc
        .perform(
            post(CLIENT_EVENTS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"event":"algorithm_completed","type":"graph","subtype":"undirected",\
                    "algorithm":"bfs","stepCount":7,"nodeCount":3}
                    """))
        .andExpect(status().isAccepted());

    GenerationEvent ev = minesOf(STUDENT_EMAIL).getLast();
    assertThat(ev.getKind()).isEqualTo("algorithm_completed");
    assertThat(ev.getAlgorithm()).isEqualTo("BFS");
    assertThat(ev.getStepCount()).isEqualTo(7);
    assertThat(ev.getStructureType()).isEqualTo("grafo_no_dirigido");
  }

  @Test
  @DisplayName("CA-2: el cliente no puede escribir identidad ni curso; los pone el servidor")
  void elClienteNoEscribeIdentidad() throws Exception {
    String token = studentToken();
    User student = users.findByEmail(STUDENT_EMAIL).orElseThrow();

    mockMvc
        .perform(
            post(CLIENT_EVENTS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"event":"algorithm_completed","type":"stack","algorithm":"push",\
                    "stepCount":2,"nodeCount":2,\
                    "pseudonym":"suplantado","courseCode":"OTRO-CURSO","userId":"999"}
                    """))
        .andExpect(status().isAccepted());

    GenerationEvent ev = minesOf(STUDENT_EMAIL).getLast();
    assertThat(ev.getPseudonym()).isEqualTo(pseudonymizer.pseudonymFor(student.getId()));
    assertThat(ev.getCourseCode()).isEqualTo("CEDI-G1");
  }

  // ── CA-3 · Solicitud fallida o fuera de alcance ──────────────────────────

  @Test
  @DisplayName("CA-3: una petición fuera de alcance queda registrada con su prompt")
  void peticionFueraDeAlcanceGuardaElPrompt() throws Exception {
    String token = studentToken();
    String prompt = "dibuja un fractal fuera de alcance por favor";

    generate(token, prompt, 503);

    GenerationEvent ev = minesOf(STUDENT_EMAIL).getLast();
    assertThat(ev.getOutcome()).isEqualTo("fuera_de_alcance");
    assertThat(ev.getPromptText()).isEqualTo(prompt);
    assertThat(ev.getInteractionSource()).isEqualTo("asistente_nlp");
  }

  @Test
  @DisplayName("CA-3: que se caiga el proveedor es 'error', no 'fuera de alcance'")
  void caidaDelProveedorSeDistingue() throws Exception {
    String token = studentToken();

    generate(token, "grafo con el proveedor caido", 503);

    assertThat(minesOf(STUDENT_EMAIL).getLast().getOutcome()).isEqualTo("error");
  }

  @Test
  @DisplayName("CA-3: el docente ve los prompts fallidos y la vista no expone seudónimos")
  void elDocenteRevisaLosFallidos() throws Exception {
    generate(studentToken(), "un grafo hiperbólico fuera de alcance", 503);
    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();

    String body =
        mockMvc
            .perform(
                get("/api/analytics/events?outcome=fuera_de_alcance&limit=20")
                    .header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<EventView> vistos = objectMapper.readValue(body, new TypeReference<>() {});
    assertThat(vistos)
        .isNotEmpty()
        .allSatisfy(v -> assertThat(v.outcome()).isEqualTo("fuera_de_alcance"));
    assertThat(vistos.getFirst().promptText()).contains("hiperbólico");
    assertThat(body).doesNotContain("pseudonym").doesNotContain("seudonimo");
  }

  @Test
  @DisplayName("CA-3: el estudiante no puede leer los eventos de revisión docente")
  void elEstudianteNoLeeLosEventos() throws Exception {
    mockMvc
        .perform(get("/api/analytics/events").header("Authorization", "Bearer " + studentToken()))
        .andExpect(status().isForbidden());
  }

  // ── CA-4 · Patrones de reintento ─────────────────────────────────────────

  @Test
  @DisplayName("CA-4: tres reformulaciones seguidas se leen como una secuencia de reintento")
  void tresReformulacionesSonUnaSecuencia() throws Exception {
    String token = studentToken();
    generate(token, "haz un árbol raro fuera de alcance", 503);
    generate(token, "bueno, un árbol rojinegro fuera de alcance", 503);
    generate(token, "un árbol rojo-negro entonces, fuera de alcance", 503);

    String sesion = minesOf(STUDENT_EMAIL).getLast().getSessionId();
    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();

    String body =
        mockMvc
            .perform(
                get("/api/analytics/retries?minAttempts=3&windowSeconds=120")
                    .header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<RetrySequence> secuencias = objectMapper.readValue(body, new TypeReference<>() {});
    assertThat(secuencias)
        .as("la sesión con tres intentos seguidos aparece como reintento")
        .anySatisfy(
            s -> {
              assertThat(s.sessionId()).isEqualTo(sesion);
              assertThat(s.attempts()).isGreaterThanOrEqualTo(3);
              assertThat(Instant.parse(s.lastAt())).isAfterOrEqualTo(Instant.parse(s.firstAt()));
            });
  }

  @Test
  @DisplayName("CA-4: un solo intento no es un patrón de reintento")
  void unIntentoNoEsPatron() throws Exception {
    String email = uniqueEmail("solo-intento");
    AuthResponse solitario = registerAs(email);
    generate(solitario.accessToken(), "crea un grafo no dirigido con 3 nodos", 200);
    String sesion = minesOf(email).getLast().getSessionId();

    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    String body =
        mockMvc
            .perform(get("/api/analytics/retries").header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<RetrySequence> secuencias = objectMapper.readValue(body, new TypeReference<>() {});
    assertThat(secuencias)
        .as("quien generó una sola vez no aparece como reintento")
        .noneSatisfy(s -> assertThat(s.sessionId()).isEqualTo(sesion));
  }

  // ── CA-5 · Seudonimización en origen ─────────────────────────────────────

  @Test
  @DisplayName("CA-5: dos cuentas distintas no comparten seudónimo, y el seudónimo no es el correo")
  void seudonimoPorCuenta() throws Exception {
    String email = uniqueEmail("analitica-otro");
    AuthResponse otro = registerAs(email);
    generate(otro.accessToken(), "crea un grafo no dirigido con 3 nodos", 200);
    generate(studentToken(), "crea un grafo no dirigido con 3 nodos", 200);

    String ajeno = minesOf(email).getLast().getPseudonym();
    String mio = minesOf(STUDENT_EMAIL).getLast().getPseudonym();
    assertThat(mio).isNotBlank().doesNotContain("@").isNotEqualTo(ajeno);
  }

  // ── CA-6 · Resiliencia ───────────────────────────────────────────────────

  @Test
  @DisplayName("CA-6: un evento del cliente irreconocible no rompe nada")
  void eventoDesconocidoNoRompe() throws Exception {
    mockMvc
        .perform(
            post(CLIENT_EVENTS)
                .header("Authorization", "Bearer " + studentToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"algo_que_no_conocemos\"}"))
        .andExpect(status().isAccepted());
  }
}
