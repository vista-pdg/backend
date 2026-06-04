package com.vista.pdg.seeder;

import com.vista.pdg.auth.entity.Permission;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.PermissionRepository;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

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
  public void run(ApplicationArguments args) {
    if (userRepo.existsByEmail("admin@vista.com")) return;

    List<Permission> perms =
        PERMISSIONS.stream()
            .map(
                p ->
                    permissionRepo
                        .findByName(p[0])
                        .orElseGet(
                            () ->
                                permissionRepo.save(
                                    Permission.builder().name(p[0]).description(p[1]).build())))
            .toList();

    Role adminRole =
        roleRepo
            .findByName("ADMIN")
            .orElseGet(
                () ->
                    roleRepo.save(
                        Role.builder().name("ADMIN").permissions(new HashSet<>(perms)).build()));

    roleRepo
        .findByName("USER")
        .orElseGet(
            () -> roleRepo.save(Role.builder().name("USER").permissions(new HashSet<>()).build()));

    userRepo.save(
        User.builder()
            .displayName("Administrador")
            .email("admin@vista.com")
            .password(passwordEncoder.encode("admin123"))
            .roles(Set.of(adminRole))
            .enabled(true)
            .build());

    log.info("Seeder: admin user created — admin@vista.com / admin123");
  }
}
