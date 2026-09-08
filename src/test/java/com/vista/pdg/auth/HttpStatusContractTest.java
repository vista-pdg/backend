package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.LoginRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifica los códigos de estado contra un servidor real, no contra MockMvc.
 *
 * <p>Existe por un defecto que la suite de MockMvc no podía ver. El contenedor reenvía los errores
 * a {@code /error}, y ese reenvío vuelve a atravesar la cadena de seguridad sin autenticación: la
 * respuesta del reenvío pisaba a la original y un 403 por rol insuficiente llegaba al cliente como
 * 401. MockMvc no ejecuta ese despacho, así que sus pruebas pasaban mientras la aplicación real
 * devolvía otra cosa.
 *
 * <p>La distinción importa de verdad: el cliente resuelve un 401 refrescando el token, y ante un
 * 403 no debe intentarlo. Con los dos colapsados, el refresco silencioso entra en un camino que no
 * lleva a ninguna parte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "gemini.api.key=test-key-no-usada",
      "auth.allowed-email-domains=u.icesi.edu.co,icesi.edu.co"
    })
class HttpStatusContractTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17")
          .withDatabaseName("vista_test")
          .withUsername("vista")
          .withPassword("vista");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort private int port;

  @Autowired private ObjectMapper objectMapper;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private String accessTokenOf(String email, String password) {
    try {
      String body = objectMapper.writeValueAsString(new LoginRequest(email, password));
      HttpResponse<String> res =
          client.send(
              HttpRequest.newBuilder(URI.create(url("/api/auth/login")))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(res.statusCode()).isEqualTo(200);
      return objectMapper.readValue(res.body(), AuthResponse.class).accessToken();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo autenticar a " + email, e);
    }
  }

  private int adminUsersStatus(String bearer) {
    try {
      HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url("/api/admin/users"))).GET();
      if (bearer != null) req.header("Authorization", "Bearer " + bearer);
      return client.send(req.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    } catch (Exception e) {
      throw new IllegalStateException("Falló la petición a /api/admin/users", e);
    }
  }

  @Test
  @DisplayName("sin token el servidor real responde 401")
  void sinTokenDevuelve401() {
    assertThat(adminUsersStatus(null)).isEqualTo(401);
  }

  /** El caso que se escapaba: el reenvío a /error convertía este 403 en un 401. */
  @Test
  @DisplayName("con rol insuficiente el servidor real responde 403, no 401")
  void rolInsuficienteDevuelve403() {
    String token = accessTokenOf("estudiante@u.icesi.edu.co", "estudiante123");

    assertThat(adminUsersStatus(token))
        .as("un 401 aquí haría que el cliente intentara refrescar un token que está perfecto")
        .isEqualTo(403);
  }

  @Test
  @DisplayName("el docente tampoco entra a administración: 403")
  void docenteDevuelve403() {
    String token = accessTokenOf("docente@u.icesi.edu.co", "docente123");
    assertThat(adminUsersStatus(token)).isEqualTo(403);
  }

  @Test
  @DisplayName("el administrador entra: 200")
  void adminDevuelve200() {
    String token = accessTokenOf("admin@vista.com", "admin123");
    assertThat(adminUsersStatus(token)).isEqualTo(200);
  }

  // ── HU-16 ────────────────────────────────────────────────────────────────

  private int statusOf(String method, String path, String bearer, String body) {
    try {
      HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url(path)));
      if (bearer != null) req.header("Authorization", "Bearer " + bearer);
      if ("POST".equals(method)) {
        req.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
      } else {
        req.GET();
      }
      return client.send(req.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    } catch (Exception e) {
      throw new IllegalStateException("Falló " + method + " " + path, e);
    }
  }

  @Test
  @DisplayName("HU-16 CA-3: el asistente anónimo responde 401 en el servidor real")
  void asistenteAnonimo401() {
    assertThat(statusOf("POST", "/api/generate", null, "{\"prompt\":\"K3\"}")).isEqualTo(401);
    assertThat(statusOf("POST", "/api/algorithm/steps", null, null)).isEqualTo(401);
  }

  @Test
  @DisplayName("HU-16 CA-4: el estudiante recibe 403 en la analítica y el docente 200")
  void analiticaSegregadaPorRol() {
    String student = accessTokenOf("estudiante@u.icesi.edu.co", "estudiante123");
    String teacher = accessTokenOf("docente@u.icesi.edu.co", "docente123");
    assertThat(statusOf("GET", "/api/analytics/summary", student, null)).isEqualTo(403);
    assertThat(statusOf("GET", "/api/analytics/summary", teacher, null)).isEqualTo(200);
    assertThat(statusOf("GET", "/api/analytics/summary", null, null)).isEqualTo(401);
  }

  @Test
  @DisplayName("un token inválido responde 401 para que el cliente lo refresque")
  void tokenInvalidoDevuelve401() {
    assertThat(adminUsersStatus("token.claramente.invalido")).isEqualTo(401);
  }
}
