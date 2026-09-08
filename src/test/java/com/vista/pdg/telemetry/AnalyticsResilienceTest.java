package com.vista.pdg.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * HU-21 · CA-6 — con la persistencia analítica caída, la aplicación sigue sirviendo.
 *
 * <p>La prueba sustituye el repositorio por uno que falla al guardar. Es la única forma honesta de
 * comprobarlo: apagar el Postgres de la suite tumbaría también la autenticación, y entonces el 200
 * no demostraría nada sobre la telemetría.
 *
 * <p>Vale la pena decir por qué esta prueba existe aparte de la unitaria. El {@code record} corre
 * en una transacción propia; si el {@code catch} quedara <em>dentro</em> del límite transaccional,
 * el fallo no saltaría al guardar sino al confirmar, ya fuera del {@code try}, y la petición del
 * estudiante moriría con un 500. Sólo pasando por la petición completa se ve esa diferencia.
 */
class AnalyticsResilienceTest extends IntegrationTestSupport {

  @MockitoBean private GenerationEventRepository events;

  @Test
  @DisplayName("CA-6: si la analítica no puede escribir, la generación responde igual")
  void laGeneracionSobreviveALaAnaliticaCaida() throws Exception {
    given(events.save(any(GenerationEvent.class)))
        .willThrow(new DataAccessResourceFailureException("analítica caída"));

    mockMvc
        .perform(
            post("/api/generate")
                .header(
                    "Authorization",
                    "Bearer " + login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"crea un grafo no dirigido con 3 nodos\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(false))
        .andExpect(jsonPath("$.nodes.length()").value(3));
  }

  @Test
  @DisplayName("CA-6: el evento del cliente también acepta el fallo sin devolver error")
  void elEventoDelClienteSobrevive() throws Exception {
    given(events.save(any(GenerationEvent.class)))
        .willThrow(new DataAccessResourceFailureException("analítica caída"));

    mockMvc
        .perform(
            post("/api/assistant/events")
                .header(
                    "Authorization",
                    "Bearer " + login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"event":"algorithm_completed","type":"graph","algorithm":"bfs",\
                    "stepCount":5,"nodeCount":3}
                    """))
        .andExpect(status().isAccepted());
  }
}
