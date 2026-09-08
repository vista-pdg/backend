package com.vista.pdg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.seeder.DataSeeder;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CA-2 — migración del rol heredado {@code USER} a {@code STUDENT}.
 *
 * <p>El proyecto no tiene Flyway y usa {@code ddl-auto=update}, así que el seeder es el único lugar
 * donde una migración de datos puede ocurrir. Estas pruebas lo ejercitan sobre una base real porque
 * lo que puede fallar —renombrar violando la unicidad, perder asignaciones de la tabla intermedia—
 * son justamente restricciones de base de datos.
 */
class RoleMigrationTest extends IntegrationTestSupport {

  @Autowired private DataSeeder seeder;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UserRepository userRepository;

  /** Deja siempre el juego de roles esperado, incluso si una aserción falló a mitad. */
  @AfterEach
  void restoreRoles() {
    seeder.run(null);
  }

  @Test
  @DisplayName("el seeder deja exactamente STUDENT, TEACHER y ADMIN")
  void juegoDeRolesEsperado() {
    assertThat(roleRepository.findAll())
        .extracting(Role::getName)
        .contains("STUDENT", "TEACHER", "ADMIN")
        .doesNotContain("USER");
  }

  @Test
  @DisplayName("USER se renombra a STUDENT conservando los usuarios que lo tenían")
  void renombraUserAStudentConservandoAsignaciones() {
    Role student = roleRepository.findByName("STUDENT").orElseThrow();
    long asignadosAntes = contarUsuariosConRol(student.getId());
    assertThat(asignadosAntes).isPositive();

    // Simula una base anterior a la HU-08.
    student.setName("USER");
    roleRepository.save(student);

    seeder.run(null);

    Role migrado = roleRepository.findByName("STUDENT").orElseThrow();
    assertThat(roleRepository.findByName("USER")).isEmpty();
    assertThat(contarUsuariosConRol(migrado.getId()))
        .as("el renombrado en sitio no debe perder ninguna asignación")
        .isEqualTo(asignadosAntes);
  }

  @Test
  @DisplayName("si USER y STUDENT coexisten, los usuarios se reasignan y USER desaparece")
  void reasignaCuandoAmbosRolesCoexisten() {
    Role legacy =
        roleRepository.save(Role.builder().name("USER").permissions(new HashSet<>()).build());
    User rezagado =
        userRepository.save(
            User.builder()
                .displayName("Rezagado")
                .email(uniqueEmail("rezagado"))
                .password("$2a$10$noimporta")
                .roles(new HashSet<>(Set.of(legacy)))
                .enabled(true)
                .build());

    seeder.run(null);

    assertThat(roleRepository.findByName("USER")).isEmpty();
    User migrado = userRepository.findById(rezagado.getId()).orElseThrow();
    assertThat(migrado.getRoles()).extracting(Role::getName).containsExactly("STUDENT");
  }

  @Test
  @DisplayName("correr el seeder dos veces no duplica roles ni usuarios")
  void seederEsIdempotente() {
    long rolesAntes = roleRepository.count();
    long usuariosAntes = userRepository.count();

    seeder.run(null);
    seeder.run(null);

    assertThat(roleRepository.count()).isEqualTo(rolesAntes);
    assertThat(userRepository.count()).isEqualTo(usuariosAntes);
  }

  /**
   * La versión anterior del seeder abortaba en cuanto existía el usuario admin. En una base ya
   * poblada —es decir, en la de cualquiera que hubiera corrido el proyecto antes— eso habría
   * impedido que la migración se ejecutara nunca.
   */
  @Test
  @DisplayName("el seeder migra aunque el usuario admin ya exista")
  void migraAunqueElAdminYaExista() {
    assertThat(userRepository.existsByEmail(ADMIN_EMAIL)).isTrue();

    Role student = roleRepository.findByName("STUDENT").orElseThrow();
    student.setName("USER");
    roleRepository.save(student);

    seeder.run(null);

    assertThat(roleRepository.findByName("STUDENT")).isPresent();
    assertThat(roleRepository.findByName("USER")).isEmpty();
  }

  private long contarUsuariosConRol(Long roleId) {
    return userRepository.findAll().stream()
        .filter(u -> u.getRoles().stream().anyMatch(r -> r.getId().equals(roleId)))
        .count();
  }
}
