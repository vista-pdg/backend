package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * HU-16 · CA-3 (el asistente exige sesión) y CA-4 (segregación de roles en la analítica).
 *
 * <p>CA-4 tiene dos mitades y las dos se comprueban: que el estudiante reciba 403 <em>y</em> que la
 * respuesta no lleve ninguna métrica. Un 403 con el cuerpo de datos adjunto cumpliría el código y
 * violaría el criterio.
 */
class AnalyticsAccessTest extends IntegrationTestSupport {

  private static final String SUMMARY = "/api/analytics/summary";
  private static final String GENERATE = "/api/generate";

  // ── CA-3 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-3: enviar una instrucción al asistente sin sesión responde 401")
  void asistenteAnonimoDevuelve401() throws Exception {
    mockMvc
        .perform(
            post(GENERATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"grafo completo de 3 vértices\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  @DisplayName("CA-3: los pasos de algoritmo tampoco son anónimos")
  void pasosAnonimosDevuelven401() throws Exception {
    mockMvc
        .perform(
            post("/api/algorithm/steps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"tree\",\"subtype\":\"avl\",\"operation\":\"insert\",\"values\":[1]}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-3: con sesión de estudiante el asistente responde")
  void asistenteConSesionResponde() throws Exception {
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"grafo completo de 3 vértices\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes.length()").value(3));
  }

  // ── CA-4 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-4: el estudiante recibe 403 y ninguna métrica en el cuerpo")
  void estudianteRecibe403SinMetricas() throws Exception {
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    String body =
        mockMvc
            .perform(get(SUMMARY).header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .as("un 403 con las métricas adjuntas cumpliría el código y violaría el criterio")
        .doesNotContain("totalGenerations")
        .doesNotContain("generationsByCourse")
        .doesNotContain("distinctUsers");
  }

  @Test
  @DisplayName("CA-4: el docente recibe 200 con agregados")
  void docenteRecibeAgregados() throws Exception {
    String token = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    mockMvc
        .perform(get(SUMMARY).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalGenerations").isNumber())
        .andExpect(jsonPath("$.distinctUsers").isNumber())
        .andExpect(jsonPath("$.generationsByCourse").isMap())
        .andExpect(jsonPath("$.generationsByStructureType").isMap());
  }

  /** El agregado no puede convertirse en una lista de personas por la puerta de atrás. */
  @Test
  @DisplayName("CA-4: la respuesta del docente no expone seudónimos ni correos individuales")
  void agregadosNoExponenIndividuos() throws Exception {
    String token = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    String body =
        mockMvc
            .perform(get(SUMMARY).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("@").doesNotContain("pseudonym").doesNotContain("email");
  }

  @Test
  @DisplayName("CA-4: el administrador tampoco accede: la analítica es sólo del docente")
  void adminRecibe403() throws Exception {
    String token = login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    mockMvc
        .perform(get(SUMMARY).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("sin sesión la analítica responde 401")
  void anonimoRecibe401() throws Exception {
    mockMvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
  }
}
