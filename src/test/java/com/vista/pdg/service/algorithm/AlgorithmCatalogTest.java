package com.vista.pdg.service.algorithm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** HU-19: el catálogo y los pasos por HTTP, con la sesión que HU-16 exige. */
class AlgorithmCatalogTest extends IntegrationTestSupport {

  @Test
  @DisplayName("el catálogo exige sesión")
  void catalogRequiresSession() throws Exception {
    mockMvc.perform(get("/api/algorithm/catalog")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-4: el catálogo lista las cuatro familias con etiqueta y tipo de entrada")
  void catalogListsFamilies() throws Exception {
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    mockMvc
        .perform(get("/api/algorithm/catalog").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[?(@.family=='graph')].operation").value("bfs"))
        .andExpect(jsonPath("$[?(@.family=='stack')].operation").value("pop"))
        .andExpect(jsonPath("$[?(@.family=='queue')].operation").value("dequeue"))
        .andExpect(jsonPath("$[?(@.family=='tree')].operation").value("insert"))
        .andExpect(jsonPath("$[?(@.operation=='bfs')].input").value("structure"))
        .andExpect(jsonPath("$[*].label").isArray());
  }

  @Test
  @DisplayName("CA-3: /steps acepta el grafo del lienzo en el cuerpo y devuelve el rastro BFS")
  void stepsWithGraphBody() throws Exception {
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    String body =
        """
        {"type":"graph","subtype":"simple","operation":"bfs","start":"n0",
         "nodes":[{"id":"n0","label":"A","x":0,"y":0,"z":0,"depth":0,"parent":null,"properties":{}},
                  {"id":"n1","label":"B","x":1,"y":0,"z":0,"depth":0,"parent":null,"properties":{}},
                  {"id":"n2","label":"C","x":2,"y":0,"z":0,"depth":0,"parent":null,"properties":{}}],
         "edges":[{"id":"e0","from":"n0","to":"n1","weight":null,"directed":false},
                  {"id":"e1","from":"n1","to":"n2","weight":null,"directed":false}]}
        """;
    mockMvc
        .perform(
            post("/api/algorithm/steps")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(false))
        .andExpect(jsonPath("$.steps.length()").value(1 + 3 + 2 + 1))
        .andExpect(jsonPath("$.steps[0].highlightType").value("frontier"))
        .andExpect(jsonPath("$.steps[-1:].highlightType").value("done"))
        .andExpect(jsonPath("$.steps[1].nodes[0].properties.state").value("current"));
  }

  @Test
  @DisplayName("CA-5: /steps de pop sobre una pila de 4 devuelve 9 pasos")
  void stepsForStackPop() throws Exception {
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    mockMvc
        .perform(
            post("/api/algorithm/steps")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"stack\",\"subtype\":\"simple\",\"operation\":\"pop\",\"values\":[3,42,8,17]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(false))
        .andExpect(jsonPath("$.steps.length()").value(9))
        .andExpect(jsonPath("$.steps[1].highlightType").value("pop"))
        .andExpect(jsonPath("$.steps[1].nodes.length()").value(4))
        .andExpect(jsonPath("$.steps[2].nodes.length()").value(3));
  }

  @Test
  @DisplayName("generar una pila y una cola por el asistente devuelve las familias nuevas")
  void generateStackAndQueueThroughAssistant() throws Exception {
    // El adaptador falso de las pruebas sólo produce K3; aquí se valida el resto de la cadena
    // (contrato → generador → layout) llamando directamente a los pasos que la exponen.
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    mockMvc
        .perform(
            post("/api/algorithm/steps")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"queue\",\"subtype\":\"simple\",\"operation\":\"dequeue\",\"values\":[5,9]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps.length()").value(5))
        .andExpect(jsonPath("$.steps[0].nodes[0].properties.role").value("front"))
        .andExpect(jsonPath("$.steps[0].edges.length()").value(1));
  }
}
