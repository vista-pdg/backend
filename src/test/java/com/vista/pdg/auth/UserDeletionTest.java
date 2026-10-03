package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.repository.RefreshTokenRepository;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class UserDeletionTest extends IntegrationTestSupport {
  @Autowired private UserRepository users;
  @Autowired private RefreshTokenRepository tokens;

  @Test
  void deletesAccountWithActiveAndRotatedSessions() throws Exception {
    var target = registerStudent("delete.sessions");
    var secondSession = login(target.email(), "clave12345");
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", target.refreshToken()))))
        .andExpect(status().isOk());
    long id = users.findByEmail(target.email()).orElseThrow().getId();
    assertThat(tokens.findAll().stream().filter(t -> t.getUser().getId().equals(id))).hasSize(3);
    var admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

    mockMvc
        .perform(
            delete("/api/admin/users/" + id)
                .header("Authorization", "Bearer " + admin.accessToken()))
        .andExpect(status().isNoContent());

    assertThat(users.existsById(id)).isFalse();
    assertThat(tokens.findAll().stream().filter(t -> t.getUser().getId().equals(id))).isEmpty();
    assertThat(users.existsByEmail(ADMIN_EMAIL)).isTrue();
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", secondSession.refreshToken()))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    mockMvc
        .perform(
            get("/api/assistant/quota").header("Authorization", "Bearer " + target.accessToken()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingAccountReturnsReadable404() throws Exception {
    var admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    mockMvc
        .perform(
            delete("/api/admin/users/9223372036854775807")
                .header("Authorization", "Bearer " + admin.accessToken()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  void onlyAdminCanDeleteAndDeniedRequestsPreserveAccount() throws Exception {
    var target = registerStudent("delete.denied");
    long id = users.findByEmail(target.email()).orElseThrow().getId();
    mockMvc.perform(delete("/api/admin/users/" + id)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            delete("/api/admin/users/" + id)
                .header("Authorization", "Bearer " + target.accessToken()))
        .andExpect(status().isForbidden());
    var teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD);
    mockMvc
        .perform(
            delete("/api/admin/users/" + id)
                .header("Authorization", "Bearer " + teacher.accessToken()))
        .andExpect(status().isForbidden());
    assertThat(users.existsById(id)).isTrue();
  }
}
