package com.vista.pdg.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * CA-2 y CA-3 — la autorización se decide por rol, y un token que no debería valer no vale.
 *
 * <p>{@code /api/admin/**} es la única ruta con restricción de rol en el backend, así que sirve de
 * banco de pruebas para el filtro JWT completo: sin token, con rol insuficiente, con token expirado
 * y con firma manipulada.
 */
class RoleAuthorizationTest extends IntegrationTestSupport {

  private static final String ADMIN_URL = "/api/admin/users";

  @Value("${jwt.secret}")
  private String secret;

  @Test
  @DisplayName("sin token no se accede a la zona de administración")
  void sinToken() throws Exception {
    mockMvc.perform(get(ADMIN_URL)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("un estudiante no accede a la zona de administración")
  void rolInsuficienteEstudiante() throws Exception {
    String token = login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("un docente tampoco accede: TEACHER no es ADMIN")
  void rolInsuficienteDocente() throws Exception {
    String token = login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("el administrador sí accede")
  void adminAccede() throws Exception {
    String token = login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("un token de acceso expirado no autentica")
  void tokenExpirado() throws Exception {
    long now = System.currentTimeMillis();
    String expirado =
        Jwts.builder()
            .subject(ADMIN_EMAIL)
            .claim("roles", List.of("ROLE_ADMIN"))
            .issuedAt(new Date(now - 7_200_000))
            .expiration(new Date(now - 3_600_000))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();

    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "Bearer " + expirado))
        .andExpect(status().isForbidden());
  }

  /** Sin esto, cualquiera podría fabricarse un token de administrador. */
  @Test
  @DisplayName("un token firmado con otra clave no autentica")
  void tokenConFirmaAjena() throws Exception {
    String falsificado =
        Jwts.builder()
            .subject(ADMIN_EMAIL)
            .claim("roles", List.of("ROLE_ADMIN"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(
                Keys.hmacShaKeyFor(
                    "clave-falsa-de-atacante-con-mas-de-32-caracteres"
                        .getBytes(StandardCharsets.UTF_8)))
            .compact();

    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "Bearer " + falsificado))
        .andExpect(status().isForbidden());
  }

  /**
   * El filtro relee los roles desde la base en cada petición en lugar de creerle al claim del
   * token. Esta prueba fija ese comportamiento: un token cuyo claim dice ADMIN pero cuyo usuario es
   * estudiante no debe conceder nada.
   */
  @Test
  @DisplayName("los roles se leen de la base, no del claim del token")
  void rolesSeLeenDeLaBaseNoDelToken() throws Exception {
    var session = registerStudent("claim.inflado");

    long now = System.currentTimeMillis();
    String inflado =
        Jwts.builder()
            .subject(session.email())
            .claim("roles", List.of("ROLE_ADMIN"))
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();

    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "Bearer " + inflado))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("una cabecera Authorization mal formada se ignora sin romper la petición")
  void cabeceraMalFormada() throws Exception {
    mockMvc
        .perform(get(ADMIN_URL).header("Authorization", "esto-no-es-un-bearer"))
        .andExpect(status().isForbidden());
  }
}
