package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.repository.RoleRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RoleDeletionTest extends IntegrationTestSupport {
  @Autowired private RoleRepository roles;

  @Test
  void assignedRoleReturnsConflictAndPreservesAccounts() throws Exception {
    var admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    var role = roles.findByName("STUDENT").orElseThrow();
    mockMvc
        .perform(
            delete("/api/admin/roles/" + role.getId())
                .header("Authorization", "Bearer " + admin.accessToken()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));
    assertThat(roles.existsById(role.getId())).isTrue();
    login(STUDENT_EMAIL, STUDENT_PASSWORD);
  }

  @Test
  void unusedRoleCanBeDeletedAndMissingRoleIs404() throws Exception {
    var admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    var role = roles.save(Role.builder().name("DELETE_TEST_" + UUID.randomUUID()).build());
    mockMvc
        .perform(
            delete("/api/admin/roles/" + role.getId())
                .header("Authorization", "Bearer " + admin.accessToken()))
        .andExpect(status().isOk());
    assertThat(roles.existsById(role.getId())).isFalse();
    mockMvc
        .perform(
            delete("/api/admin/roles/" + role.getId())
                .header("Authorization", "Bearer " + admin.accessToken()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
  }
}
