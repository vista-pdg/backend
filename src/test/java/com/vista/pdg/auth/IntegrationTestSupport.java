package com.vista.pdg.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.testsupport.FakeLlmConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integración de autenticación.
 *
 * <p>Levanta un Postgres real en vez de mockear repositorios: lo que se está probando
 * —restricciones de unicidad, el {@code @Modifying} que revoca una familia entera, el
 * comportamiento transaccional de la detección de reuso— sólo se manifiesta contra una base de
 * verdad. Con repositorios simulados estas pruebas pasarían sin demostrar nada.
 *
 * <p>El contenedor es único para toda la suite (patrón singleton, arrancado en el bloque estático
 * en lugar de con {@code @Container}) para no pagar un arranque por clase de prueba. Ryuk lo retira
 * al terminar la JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLlmConfig.class)
@TestPropertySource(
    properties = {
      // Evita que el arranque del contexto dependa de un .env con clave real de Gemini.
      "gemini.api.key=test-key-no-usada",
      "auth.allowed-email-domains=u.icesi.edu.co,icesi.edu.co",
      "auth.min-password-length=8",
      // El limitador de tasa se prueba aparte con su propio reloj. Aquí se sube para que las suites
      // que generan varias veces seguidas con la misma cuenta (telemetría) no choquen con él.
      "assistant.rate.per-minute=1000",
      "spring.jpa.hibernate.ddl-auto=update"
    })
public abstract class IntegrationTestSupport {

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

  /**
   * Las pruebas comparten una única base, así que cada una necesita correos que no choquen con los
   * de las demás. Un contador es suficiente y hace los fallos legibles, a diferencia de un UUID.
   */
  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  protected String uniqueEmail(String prefix) {
    return prefix + SEQ.incrementAndGet() + "@u.icesi.edu.co";
  }

  @BeforeEach
  void assertContainerIsUp() {
    org.assertj.core.api.Assertions.assertThat(POSTGRES.isRunning()).isTrue();
  }

  protected String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo serializar el cuerpo de la petición", e);
    }
  }

  // ── Atajos de autenticación ────────────────────────────────────────────

  /** Registra un estudiante nuevo y devuelve su par de tokens. */
  protected AuthResponse registerStudent(String emailPrefix) throws Exception {
    String body =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/auth/register")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new RegisterRequest(
                                "Prueba " + emailPrefix,
                                uniqueEmail(emailPrefix),
                                "clave12345",
                                "clave12345",
                                "CEDI-G1"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, AuthResponse.class);
  }

  protected AuthResponse login(String email, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/auth/login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(json(new LoginRequest(email, password))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, AuthResponse.class);
  }

  /** Usuarios sembrados por {@code DataSeeder}, disponibles en toda la suite. */
  protected static final String ADMIN_EMAIL = "admin@vista.com";

  protected static final String ADMIN_PASSWORD = "admin123";
  protected static final String TEACHER_EMAIL = "docente@u.icesi.edu.co";
  protected static final String TEACHER_PASSWORD = "docente123";
  protected static final String STUDENT_EMAIL = "estudiante@u.icesi.edu.co";
  protected static final String STUDENT_PASSWORD = "estudiante123";
}
