package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** CA-1 — registro desde la pantalla de bienvenida. */
class AuthRegistrationTest extends IntegrationTestSupport {

  @Autowired private UserRepository userRepository;

  private static final String URL = "/api/auth/register";

  @Test
  @DisplayName("registro válido devuelve 201, par de tokens y rol STUDENT")
  void registroValido() throws Exception {
    String email = uniqueEmail("ana.restrepo");

    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(new RegisterRequest("Ana Restrepo", email, "clave12345", "clave12345"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.displayName").value("Ana Restrepo"))
        .andExpect(jsonPath("$.roles.length()").value(1))
        .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
  }

  @Test
  @DisplayName("la contraseña se guarda cifrada, nunca en claro")
  void contrasenaCifrada() throws Exception {
    String email = uniqueEmail("cifrada");

    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest("Cifrada", email, "clave12345", "clave12345"))))
        .andExpect(status().isCreated());

    User saved = userRepository.findByEmail(email).orElseThrow();
    assertThat(saved.getPassword()).isNotEqualTo("clave12345").startsWith("$2");
  }

  @Test
  @DisplayName("el correo se normaliza a minúsculas para que no haya cuentas duplicadas por caja")
  void correoNormalizado() throws Exception {
    String email = uniqueEmail("MayUsculas");

    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new RegisterRequest(
                            "Mayúsculas", email.toUpperCase(), "clave12345", "clave12345"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email.toLowerCase()));

    assertThat(userRepository.findByEmail(email.toLowerCase())).isPresent();
  }

  @Test
  @DisplayName("correo fuera del dominio institucional devuelve 422 señalando el campo email")
  void correoNoInstitucional() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new RegisterRequest(
                            "Externa", "ana@gmail.com", "clave12345", "clave12345"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.email").exists());
  }

  @Test
  @DisplayName("contraseñas que no coinciden devuelven 422 señalando confirmPassword")
  void contrasenasDistintas() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new RegisterRequest(
                            "Ana", uniqueEmail("mismatch"), "clave12345", "otraclave"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.confirmPassword").exists());
  }

  @Test
  @DisplayName("contraseña más corta que el mínimo devuelve 422 señalando password")
  void contrasenaCorta() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest("Ana", uniqueEmail("corta"), "corta", "corta"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
  }

  @Test
  @DisplayName("campos vacíos los rechaza Bean Validation con 422 por cada campo")
  void camposVacios() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("displayName", "", "email", "", "password", ""))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.displayName").exists())
        .andExpect(jsonPath("$.fieldErrors.email").exists())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
  }

  @Test
  @DisplayName("correo ya registrado devuelve 409 y no crea una segunda cuenta")
  void correoDuplicado() throws Exception {
    String email = uniqueEmail("duplicada");
    String body = json(new RegisterRequest("Ana", email, "clave12345", "clave12345"));

    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
        .andExpect(jsonPath("$.fieldErrors.email").exists());

    assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals(email)))
        .hasSize(1);
  }

  @Test
  @DisplayName("el registro nunca concede TEACHER ni ADMIN, aunque sea lo que más se intentaría")
  void registroNoConcedeRolesElevados() throws Exception {
    String email = uniqueEmail("escalada");

    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "displayName", "Escalada",
                            "email", email,
                            "password", "clave12345",
                            "confirmPassword", "clave12345",
                            "roles", java.util.List.of("ADMIN", "TEACHER")))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
        .andExpect(jsonPath("$.roles.length()").value(1));

    User saved = userRepository.findByEmail(email).orElseThrow();
    assertThat(saved.getRoles()).extracting("name").containsExactly("STUDENT");
  }
}
