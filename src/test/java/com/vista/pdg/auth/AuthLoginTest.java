package com.vista.pdg.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** CA-1 y CA-3 — inicio de sesión y roles expuestos al frontend. */
class AuthLoginTest extends IntegrationTestSupport {

  @Autowired private UserRepository userRepository;

  private static final String URL = "/api/auth/login";

  private void expectLogin(String email, String password, String rol) throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(email, password))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.roles[0]").value(rol));
  }

  @Test
  @DisplayName("el estudiante inicia sesión y recibe rol STUDENT")
  void loginEstudiante() throws Exception {
    expectLogin(STUDENT_EMAIL, STUDENT_PASSWORD, "STUDENT");
  }

  /** El rol docente es lo que el frontend usa para decidir la redirección de CA-3. */
  @Test
  @DisplayName("el docente inicia sesión y recibe rol TEACHER, que es lo que gobierna CA-3")
  void loginDocente() throws Exception {
    expectLogin(TEACHER_EMAIL, TEACHER_PASSWORD, "TEACHER");
  }

  @Test
  @DisplayName("el administrador inicia sesión y recibe rol ADMIN")
  void loginAdmin() throws Exception {
    expectLogin(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
  }

  @Test
  @DisplayName("contraseña incorrecta devuelve 401 sin revelar si el correo existe")
  void contrasenaIncorrecta() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(STUDENT_EMAIL, "claveequivocada"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("Credenciales incorrectas"));
  }

  @Test
  @DisplayName("usuario inexistente devuelve el mismo 401 y el mismo mensaje que una clave errónea")
  void usuarioInexistente() throws Exception {
    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest("nadie@u.icesi.edu.co", "clave12345"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("Credenciales incorrectas"));
  }

  @Test
  @DisplayName("usuario deshabilitado devuelve 403 y no entrega tokens")
  void usuarioDeshabilitado() throws Exception {
    var session = registerStudent("deshabilitado");
    User user = userRepository.findByEmail(session.email()).orElseThrow();
    user.setEnabled(false);
    userRepository.save(user);

    mockMvc
        .perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(session.email(), "clave12345"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_DISABLED"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());
  }

  @Test
  @DisplayName("el correo entra sin distinguir mayúsculas, como lo escribiría el usuario")
  void correoInsensibleACaja() throws Exception {
    expectLogin(STUDENT_EMAIL.toUpperCase(), STUDENT_PASSWORD, "STUDENT");
  }

  @Test
  @DisplayName("cuerpo vacío lo rechaza Bean Validation con 422")
  void cuerpoVacio() throws Exception {
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(Map.of())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("la respuesta nunca incluye el hash de la contraseña")
  void respuestaNoFiltraContrasena() throws Exception {
    String body =
        mockMvc
            .perform(
                post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new LoginRequest(STUDENT_EMAIL, STUDENT_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("password")
        .doesNotContain("$2a$")
        .doesNotContain("$2b$");
  }
}
