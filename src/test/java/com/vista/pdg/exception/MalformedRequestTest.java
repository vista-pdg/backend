package com.vista.pdg.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Un cuerpo que no se puede leer es culpa de quien llama, no del servidor.
 *
 * <p>Antes acababa en el manejador genérico y salía un 500 que además devolvía el mensaje de la
 * excepción tal cual: en el peor caso, la consulta SQL entera con sus nombres de columnas. Estas
 * pruebas fijan las dos mitades del arreglo —el código correcto y el silencio sobre lo de dentro—
 * porque las dos se pierden con facilidad al volver a tocar el manejador.
 */
class MalformedRequestTest extends IntegrationTestSupport {

  private static final String STEPS = "/api/algorithm/steps";

  /** Un nodo al que le faltan las coordenadas, que son primitivas y no admiten nulos. */
  private static final String NODO_SIN_COORDENADAS =
      """
      {"type":"graph","subtype":"simple","operation":"bfs",\
      "nodes":[{"id":"a","label":"A"}],"edges":[],"start":"a"}
      """;

  private MockHttpServletRequestBuilder steps(String body) throws Exception {
    return post(STEPS)
        .header("Authorization", "Bearer " + login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  @Test
  @DisplayName("un nodo sin coordenadas responde 400 y señala el campo, no 500")
  void nodoSinCoordenadas() throws Exception {
    mockMvc
        .perform(steps(NODO_SIN_COORDENADAS))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.message").value(containsString("nodes[0].x")))
        .andExpect(jsonPath("$.fieldErrors['nodes[0].x']").exists());
  }

  @Test
  @DisplayName("una arista sin el indicador de sentido también responde 400")
  void aristaSinSentido() throws Exception {
    mockMvc
        .perform(
            steps(
                """
                {"type":"graph","subtype":"simple","operation":"bfs",\
                "nodes":[{"id":"a","label":"A","x":0,"y":0,"z":0,"depth":0}],\
                "edges":[{"id":"e","source":"a","target":"a"}],"start":"a"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.message").value(containsString("edges[0].directed")));
  }

  @Test
  @DisplayName("un JSON roto responde 400 sin inventarse un campo culpable")
  void jsonRoto() throws Exception {
    mockMvc
        .perform(steps("{\"type\":\"graph\","))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  @DisplayName("la respuesta de error no filtra tipos Java ni el detalle de la excepción")
  void sinDetallesInternos() throws Exception {
    mockMvc
        .perform(steps(NODO_SIN_COORDENADAS))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(not(containsString("com.vista.pdg"))))
        .andExpect(content().string(not(containsString("Exception"))))
        .andExpect(content().string(not(containsString("DeserializationFeature"))));
  }
}
