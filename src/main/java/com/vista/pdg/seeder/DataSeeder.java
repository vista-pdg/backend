package com.vista.pdg.seeder;

import com.vista.pdg.auth.entity.Permission;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.PermissionRepository;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja la base en el estado que la aplicación espera al arrancar.
 *
 * <p>Todo aquí es idempotente y, sobre todo, <b>no hay salida temprana</b>: la versión anterior
 * abortaba en cuanto existía el usuario admin, lo que habría hecho que la migración de roles nunca
 * corriera en una base ya poblada. Como el proyecto usa {@code ddl-auto=update} y no tiene Flyway,
 * este runner es el único lugar donde una migración de datos puede ocurrir de forma fiable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

  private static final String LEGACY_STUDENT_ROLE = "USER";
  private static final String STUDENT = "STUDENT";
  private static final String TEACHER = "TEACHER";
  private static final String ADMIN = "ADMIN";

  private final PermissionRepository permissionRepo;
  private final RoleRepository roleRepo;
  private final UserRepository userRepo;
  private final PasswordEncoder passwordEncoder;

  private static final List<String[]> PERMISSIONS =
      List.of(
          new String[] {"USERS_READ", "Ver lista de usuarios"},
          new String[] {"USERS_WRITE", "Crear y editar usuarios"},
          new String[] {"USERS_DELETE", "Eliminar usuarios"},
          new String[] {"ROLES_READ", "Ver lista de roles"},
          new String[] {"ROLES_WRITE", "Crear y editar roles"},
          new String[] {"ROLES_DELETE", "Eliminar roles"},
          new String[] {"PERMISSIONS_READ", "Ver lista de permisos"});

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<Permission> perms = ensurePermissions();

    Role student = migrateLegacyStudentRole();
    Role teacher = ensureRole(TEACHER);
    Role admin = ensureRole(ADMIN);

    admin.setPermissions(new HashSet<>(perms));
    roleRepo.save(admin);

    ensureUser("admin@vista.com", "Administrador", "admin123", admin);
    ensureUser("docente@u.icesi.edu.co", "Docente Demo", "docente123", teacher);
    ensureUser("estudiante@u.icesi.edu.co", "Estudiante Demo", "estudiante123", student);
  }

  private List<Permission> ensurePermissions() {
    return PERMISSIONS.stream()
        .map(
            p ->
                permissionRepo
                    .findByName(p[0])
                    .orElseGet(
                        () ->
                            permissionRepo.save(
                                Permission.builder().name(p[0]).description(p[1]).build())))
        .toList();
  }

  /**
   * Convierte el rol {@code USER} heredado en {@code STUDENT}.
   *
   * <p>El caso normal es un renombrado en sitio, que preserva las asignaciones existentes sin tocar
   * la tabla intermedia. Sólo si por alguna razón ya conviven ambos roles hay que reasignar
   * usuarios uno a uno y borrar el viejo.
   */
  private Role migrateLegacyStudentRole() {
    Optional<Role> legacy = roleRepo.findByName(LEGACY_STUDENT_ROLE);
    Optional<Role> student = roleRepo.findByName(STUDENT);

    if (legacy.isPresent() && student.isEmpty()) {
      Role role = legacy.get();
      role.setName(STUDENT);
      log.info("Seeder: rol USER renombrado a STUDENT (asignaciones preservadas)");
      return roleRepo.save(role);
    }

    if (legacy.isPresent()) {
      Role old = legacy.get();
      Role target = student.get();
      int moved = 0;
      for (User u : userRepo.findAll()) {
        if (u.getRoles().removeIf(r -> r.getId().equals(old.getId()))) {
          u.getRoles().add(target);
          userRepo.save(u);
          moved++;
        }
      }
      roleRepo.delete(old);
      log.info("Seeder: rol USER eliminado, {} usuario(s) reasignados a STUDENT", moved);
      return target;
    }

    return student.orElseGet(() -> createRole(STUDENT));
  }

  private Role ensureRole(String name) {
    return roleRepo.findByName(name).orElseGet(() -> createRole(name));
  }

  private Role createRole(String name) {
    log.info("Seeder: rol {} creado", name);
    return roleRepo.save(Role.builder().name(name).permissions(new HashSet<>()).build());
  }

  private void ensureUser(String email, String displayName, String rawPassword, Role role) {
    if (userRepo.existsByEmail(email)) return;
    userRepo.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .password(passwordEncoder.encode(rawPassword))
            .roles(new HashSet<>(Set.of(role)))
            .enabled(true)
            .build());
    log.info("Seeder: usuario {} creado con rol {}", email, role.getName());
  }
}
