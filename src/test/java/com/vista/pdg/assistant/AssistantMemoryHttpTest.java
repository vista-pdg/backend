package com.vista.pdg.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.assistant.session.AssistantSessionService;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.controller.StructureController;
import com.vista.pdg.service.llm.def.ConversationContext;
import com.vista.pdg.testsupport.FakeLlmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * HU-32 por HTTP: que la sesión viaje hasta el modelo y vuelva. Lo que el modelo haga con el
 * contexto es cosa suya (y se prueba con el stub y en E2E); aquí se fija el contrato del backend.
 */
class AssistantMemoryHttpTest extends IntegrationTestSupport {

  private static final String GENERATE = "/api/generate";
  private static final String SESSION = "/api/assistant/session";

  @Autowired private AssistantSessionService sessions;
  @Autowired private UserRepository users;

  @BeforeEach
  void resetAdapter() {
    FakeLlmConfig.LAST_CONTEXT.set(null);
  }

  private void generate(String token, String prompt) throws Exception {
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(StructureController.MEMORY_HEADER, "active"));
  }

  @Test
  @DisplayName("la primera instrucción va sin contexto y deja la estructura como sesión vigente")
  void firstMessageHasNoContextAndCreatesSession() throws Exception {
    AuthResponse s = registerStudent("http.memoria.primera");
    Long id = users.findByEmail(s.email()).orElseThrow().getId();

    generate(s.accessToken(), "grafo completo de 3 vertices");

    assertThat(FakeLlmConfig.LAST_CONTEXT.get().isEmpty()).isTrue();
    assertThat(sessions.load(id).hasStructure()).isTrue();
    assertThat(sessions.load(id).structureType()).isEqualTo("graph");
    assertThat(sessions.load(id).asLines().getFirst())
        .isEqualTo("usuario: grafo completo de 3 vertices");
    assertThat(sessions.load(id).asLines().getLast()).contains("graph").contains("3 nodos");
  }

  @Test
  @DisplayName("CA-1: la segunda instrucción llega al modelo con el contrato vigente y los turnos")
  void secondMessageCarriesTheSession() throws Exception {
    AuthResponse s = registerStudent("http.memoria.segunda");
    generate(s.accessToken(), "grafo completo de 3 vertices");
    generate(s.accessToken(), "ahora inserta el 7");

    ConversationContext ctx = FakeLlmConfig.LAST_CONTEXT.get();
    assertThat(ctx.isEmpty()).isFalse();
    assertThat(ctx.currentContract()).contains("\"type\":\"graph\"").contains("\"labels\"");
    assertThat(ctx.recentTurns()).anyMatch(t -> t.contains("grafo completo de 3 vertices"));
    assertThat(ctx.asPromptBlock()).contains("CURRENT STRUCTURE").endsWith("NEW INSTRUCTION:\n");
  }

  @Test
  @DisplayName("CA-6: la sesión de otro estudiante no llega en mi contexto")
  void contextIsPerUser() throws Exception {
    AuthResponse other = registerStudent("http.memoria.otro");
    generate(other.accessToken(), "grafo completo de 3 vertices");

    AuthResponse mine = registerStudent("http.memoria.mio");
    generate(mine.accessToken(), "inserta el 9");

    assertThat(FakeLlmConfig.LAST_CONTEXT.get().isEmpty()).isTrue();
  }

  @Test
  @DisplayName("CA-7: DELETE borra la sesión y la instrucción siguiente vuelve a ir sin contexto")
  void deleteClearsTheSession() throws Exception {
    AuthResponse s = registerStudent("http.memoria.borrado");
    generate(s.accessToken(), "grafo completo de 3 vertices");

    mockMvc
        .perform(delete(SESSION).header("Authorization", "Bearer " + s.accessToken()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(SESSION).header("Authorization", "Bearer " + s.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.active").value(false));

    generate(s.accessToken(), "inserta el 9");
    assertThat(FakeLlmConfig.LAST_CONTEXT.get().isEmpty()).isTrue();
  }

  @Test
  @DisplayName("el estado de la sesión dice el tipo vigente y cuánto le queda")
  void statusEndpointReportsTheSession() throws Exception {
    AuthResponse s = registerStudent("http.memoria.estado");
    generate(s.accessToken(), "grafo completo de 3 vertices");

    mockMvc
        .perform(get(SESSION).header("Authorization", "Bearer " + s.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.structureType").value("graph"))
        .andExpect(jsonPath("$.secondsRemaining").isNumber());
  }

  @Test
  @DisplayName("la sesión exige autenticación, como el resto del asistente")
  void sessionEndpointsRequireSession() throws Exception {
    mockMvc.perform(get(SESSION)).andExpect(status().isUnauthorized());
    mockMvc.perform(delete(SESSION)).andExpect(status().isUnauthorized());
  }
}
