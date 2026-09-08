package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.entity.RefreshToken;
import com.vista.pdg.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** CA-2 — rotación del token de refresco y detección de reuso. */
class RefreshTokenRotationTest extends IntegrationTestSupport {

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  private static final String REFRESH = "/api/auth/refresh";
  private static final String LOGOUT = "/api/auth/logout";

  private String refreshBody(String token) {
    return json(Map.of("refreshToken", token));
  }

  private RefreshToken rowOf(String rawToken) {
    return refreshTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
  }

  private static String sha256(String raw) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(d.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("el refresco devuelve un par nuevo y el token entregado cambia en cada rotación")
  void rotacionEntregaTokenNuevo() throws Exception {
    AuthResponse session = registerStudent("rotacion");

    String body =
        mockMvc
            .perform(
                post(REFRESH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody(session.refreshToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    AuthResponse rotated = objectMapper.readValue(body, AuthResponse.class);
    assertThat(rotated.refreshToken()).isNotEqualTo(session.refreshToken());
    assertThat(rotated.email()).isEqualTo(session.email());
  }

  @Test
  @DisplayName("la rotación mantiene la familia: el eslabón nuevo hereda el familyId del anterior")
  void rotacionConservaLaFamilia() throws Exception {
    AuthResponse session = registerStudent("familia");
    String familiaOriginal = rowOf(session.refreshToken()).getFamilyId();

    AuthResponse rotated = rotate(session.refreshToken());

    assertThat(rowOf(rotated.refreshToken()).getFamilyId()).isEqualTo(familiaOriginal);
  }

  @Test
  @DisplayName("presentar un token ya rotado devuelve 401 TOKEN_REUSE_DETECTED")
  void reusoDeTokenRotado() throws Exception {
    AuthResponse session = registerStudent("reuso");
    rotate(session.refreshToken());

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(session.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));
  }

  /**
   * Regresión del defecto más peligroso de esta HU.
   *
   * <p>{@code rotate()} revoca la familia y acto seguido lanza la excepción. Sin {@code
   * noRollbackFor}, esa excepción revierte la transacción y con ella la revocación: el cliente
   * recibe su 401 y todo <em>parece</em> correcto, pero el token robado sigue sirviendo. La única
   * forma de distinguir ambos mundos es comprobar que, tras el reuso, el token legítimo más
   * reciente también dejó de funcionar.
   */
  @Test
  @DisplayName("tras detectar reuso la familia queda revocada de verdad, también el token vigente")
  void reusoRevocaLaFamiliaCompleta() throws Exception {
    AuthResponse session = registerStudent("revocacion");
    AuthResponse vigente = rotate(session.refreshToken());
    String familyId = rowOf(vigente.refreshToken()).getFamilyId();

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(session.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));

    // El token que hasta hace un instante era válido ya no sirve.
    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(vigente.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // Y la revocación está persistida, no sólo reflejada en la respuesta.
    List<RefreshToken> familia = refreshTokenRepository.findByFamilyId(familyId);
    assertThat(familia).isNotEmpty();
    assertThat(familia).allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
  }

  /**
   * Regresión de la limpieza de tokens. Si {@code deleteExpiredTokens} borrara también los ya
   * usados —como hacía la primera versión—, el reuso dejaría de detectarse: el token rotado ya no
   * estaría en la tabla y el segundo intento devolvería «desconocido» en lugar de cortar la
   * familia.
   */
  @Test
  @DisplayName("un login posterior no borra los tokens usados, que son los que delatan el reuso")
  void loginPosteriorNoBorraTokensUsados() throws Exception {
    AuthResponse session = registerStudent("limpieza");
    String email = session.email();
    rotate(session.refreshToken());

    // Un login nuevo dispara la limpieza oportunista.
    login(email, "clave12345");

    assertThat(refreshTokenRepository.findByTokenHash(sha256(session.refreshToken())))
        .as("el token rotado debe seguir en la tabla para poder detectar su reuso")
        .isPresent();

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(session.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));
  }

  @Test
  @DisplayName("un token de refresco desconocido devuelve 401 sin revocar nada")
  void tokenDesconocido() throws Exception {
    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody("token-que-nunca-se-emitio")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  @DisplayName("un token de refresco expirado devuelve 401 y no se confunde con un reuso")
  void tokenExpirado() throws Exception {
    AuthResponse session = registerStudent("expirado");

    RefreshToken row = rowOf(session.refreshToken());
    row.setExpiresAt(Instant.now().minusSeconds(60));
    refreshTokenRepository.save(row);

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(session.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  @DisplayName("el logout revoca la familia y el refresco posterior no lanza alarma de reuso")
  void logoutRevocaSinAlarma() throws Exception {
    AuthResponse session = registerStudent("logout");

    mockMvc
        .perform(
            post(LOGOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(session.refreshToken())))
        .andExpect(status().isNoContent());

    // Revocado pero nunca usado: es un token viejo, no un ataque. Confundirlo con un reuso le
    // mostraría al usuario una advertencia de seguridad tras un cierre de sesión normal.
    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(session.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  @DisplayName("el logout es idempotente: repetirlo sigue devolviendo 204")
  void logoutIdempotente() throws Exception {
    AuthResponse session = registerStudent("logout.doble");
    String body = refreshBody(session.refreshToken());

    mockMvc
        .perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("cerrar una sesión no afecta a las demás sesiones del mismo usuario")
  void logoutNoAfectaOtrasSesiones() throws Exception {
    AuthResponse primera = registerStudent("multisesion");
    AuthResponse segunda = login(primera.email(), "clave12345");

    assertThat(rowOf(primera.refreshToken()).getFamilyId())
        .as("cada login debe abrir su propia familia")
        .isNotEqualTo(rowOf(segunda.refreshToken()).getFamilyId());

    mockMvc
        .perform(
            post(LOGOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(primera.refreshToken())))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(segunda.refreshToken())))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("el token de refresco no se guarda en claro")
  void tokenNoSeGuardaEnClaro() throws Exception {
    AuthResponse session = registerStudent("hash");

    assertThat(refreshTokenRepository.findByTokenHash(session.refreshToken()))
        .as("buscar por el valor en claro no debe encontrar nada")
        .isEmpty();
    assertThat(rowOf(session.refreshToken()).getTokenHash())
        .isNotEqualTo(session.refreshToken())
        .hasSize(64);
  }

  private AuthResponse rotate(String refreshToken) throws Exception {
    String body =
        mockMvc
            .perform(
                post(REFRESH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody(refreshToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, AuthResponse.class);
  }
}
