package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import com.vista.pdg.telemetry.service.Pseudonymizer;
import com.vista.pdg.telemetry.service.TelemetryService;
import com.vista.pdg.telemetry.service.VisualizationMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-18 · CA-7 (registro del modo de visualización).
 *
 * <p>El modo viaja en la cabecera {@code X-Visualization-Mode}, que el cliente añade a toda
 * petición con el modo que tiene activo. Aquí se comprueba que queda en la fila del evento tanto
 * para una generación como para una ejecución de algoritmo —que hasta esta HU no generaba evento
 * alguno—, que una cabecera ausente o inválida no rompe nada y que el desglose llega a la analítica
 * del docente.
 */
class VisualizationModeTelemetryTest extends IntegrationTestSupport {

  private static final String GENERATE = "/api/generate";
  private static final String STEPS = "/api/algorithm/steps";
  private static final String SUMMARY = "/api/analytics/summary";
  private static final String AVL =
      "{\"type\":\"tree\",\"subtype\":\"avl\",\"operation\":\"insert\",\"values\":[10,5,3]}";

  @Autowired private GenerationEventRepository events;
  @Autowired private UserRepository users;
  @Autowired private Pseudonymizer pseudonymizer;

  private String pseudonymOf(AuthResponse session) {
    return pseudonymizer.pseudonymFor(users.findByEmail(session.email()).orElseThrow().getId());
  }

  private static MockHttpServletRequestBuilder authed(
      MockHttpServletRequestBuilder b, String token, String body) {
    return b.header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private GenerationEvent lastEventOf(AuthResponse session) {
    List<GenerationEvent> mine = events.findByPseudonym(pseudonymOf(session));
    assertThat(mine).isNotEmpty();
    return mine.getLast();
  }

  @Test
  @DisplayName("CA-7: una generación en modo 2D queda registrada con visualizationMode = 2D")
  void generacionRegistraElModo2D() throws Exception {
    AuthResponse s = registerStudent("modo.gen");
    mockMvc
        .perform(
            authed(post(GENERATE), s.accessToken(), "{\"prompt\":\"grafo K3\"}")
                .header(VisualizationMode.HEADER, "2D"))
        .andExpect(status().isOk());

    GenerationEvent ev = lastEventOf(s);
    assertThat(ev.getVisualizationMode()).isEqualTo("2D");
    assertThat(ev.getKind()).isEqualTo(TelemetryService.KIND_GENERATION);
  }

  @Test
  @DisplayName("CA-7: ejecutar un algoritmo en 3D crea un evento de tipo algorithm con el modo")
  void algoritmoRegistraEventoConModo() throws Exception {
    AuthResponse s = registerStudent("modo.alg");
    assertThat(events.findByPseudonym(pseudonymOf(s))).isEmpty();

    mockMvc
        .perform(authed(post(STEPS), s.accessToken(), AVL).header(VisualizationMode.HEADER, "3d"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(false));

    GenerationEvent ev = lastEventOf(s);
    assertThat(ev.getKind()).isEqualTo(TelemetryService.KIND_ALGORITHM);
    assertThat(ev.getVisualizationMode()).as("se normaliza a mayúsculas").isEqualTo("3D");
    assertThat(ev.getStructureType()).isEqualTo("tree");
    assertThat(ev.getSubtype()).isEqualTo("avl");
    assertThat(ev.getNodeCount()).as("nodos del estado final del rastro").isEqualTo(3);
    assertThat(ev.getCourseCode()).isEqualTo("CEDI-G1");
  }

  @Test
  @DisplayName("CA-7: el mismo algoritmo en 2D y en 3D produce dos eventos con modos distintos")
  void ambosModosQuedanDiferenciados() throws Exception {
    AuthResponse s = registerStudent("modo.ambos");
    for (String mode : List.of("2D", "3D")) {
      mockMvc
          .perform(authed(post(STEPS), s.accessToken(), AVL).header(VisualizationMode.HEADER, mode))
          .andExpect(status().isOk());
    }
    assertThat(events.findByPseudonym(pseudonymOf(s)))
        .extracting(GenerationEvent::getVisualizationMode)
        .containsExactly("2D", "3D");
  }

  @Test
  @DisplayName("sin cabecera el evento se registra igual, con el modo en nulo")
  void sinCabeceraElModoEsNulo() throws Exception {
    AuthResponse s = registerStudent("modo.sin");
    mockMvc.perform(authed(post(STEPS), s.accessToken(), AVL)).andExpect(status().isOk());
    assertThat(lastEventOf(s).getVisualizationMode()).isNull();
  }

  @Test
  @DisplayName("una cabecera con un valor desconocido no rechaza la petición ni se persiste")
  void cabeceraInvalidaSeIgnora() throws Exception {
    AuthResponse s = registerStudent("modo.raro");
    mockMvc
        .perform(authed(post(STEPS), s.accessToken(), AVL).header(VisualizationMode.HEADER, "4D"))
        .andExpect(status().isOk());
    assertThat(lastEventOf(s).getVisualizationMode()).isNull();
  }

  @Test
  @DisplayName("una petición de algoritmo rechazada no genera evento")
  void algoritmoRechazadoNoRegistra() throws Exception {
    AuthResponse s = registerStudent("modo.vacio");
    mockMvc
        .perform(
            authed(
                    post(STEPS),
                    s.accessToken(),
                    "{\"type\":\"tree\",\"subtype\":\"avl\",\"operation\":\"insert\",\"values\":[]}")
                .header(VisualizationMode.HEADER, "2D"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(true));
    mockMvc
        .perform(
            authed(
                    post(STEPS),
                    s.accessToken(),
                    "{\"type\":\"graph\",\"subtype\":\"x\",\"operation\":\"bfs\",\"values\":[1]}")
                .header(VisualizationMode.HEADER, "2D"))
        .andExpect(jsonPath("$.error").value(true));
    assertThat(events.findByPseudonym(pseudonymOf(s))).isEmpty();
  }

  @Test
  @DisplayName("CA-7: la analítica del docente desglosa los eventos por modo de visualización")
  void analiticaDesglosaPorModo() throws Exception {
    AuthResponse s = registerStudent("modo.analitica");
    mockMvc
        .perform(authed(post(STEPS), s.accessToken(), AVL).header(VisualizationMode.HEADER, "2D"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            authed(post(GENERATE), s.accessToken(), "{\"prompt\":\"grafo K3\"}")
                .header(VisualizationMode.HEADER, "3D"))
        .andExpect(status().isOk());

    String teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    String body =
        mockMvc
            .perform(get(SUMMARY).header("Authorization", "Bearer " + teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventsByVisualizationMode").isMap())
            .andExpect(jsonPath("$.totalAlgorithmRuns").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode byMode = objectMapper.readTree(body).get("eventsByVisualizationMode");
    assertThat(byMode.get("2D").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(byMode.get("3D").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(byMode.has("null")).as("los eventos sin modo no aparecen en el desglose").isFalse();

    JsonNode root = objectMapper.readTree(body);
    assertThat(root.get("totalAlgorithmRuns").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(root.get("totalGenerations").asLong()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("la normalización acepta 2D/3D en cualquier caja y descarta el resto")
  void normalizacion() {
    assertThat(VisualizationMode.normalize("2D")).isEqualTo("2D");
    assertThat(VisualizationMode.normalize(" 3d ")).isEqualTo("3D");
    assertThat(VisualizationMode.normalize("4D")).isNull();
    assertThat(VisualizationMode.normalize("")).isNull();
    assertThat(VisualizationMode.normalize(null)).isNull();
  }
}
