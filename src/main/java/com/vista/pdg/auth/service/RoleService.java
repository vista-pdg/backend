package com.vista.pdg.auth.service;

import com.vista.pdg.auth.dto.CreateRoleRequest;
import com.vista.pdg.auth.dto.RoleDto;
import com.vista.pdg.auth.entity.Permission;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.repository.PermissionRepository;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final UserRepository userRepository;

  public List<RoleDto> findAll() {
    return roleRepository.findAll().stream().map(this::toDto).toList();
  }

  public RoleDto create(CreateRoleRequest req) {
    Role role =
        Role.builder()
            .name(req.name())
            .permissions(new HashSet<>(permissionRepository.findAllById(req.permissionIds())))
            .build();
    return toDto(roleRepository.save(role));
  }

  public RoleDto update(Long id, CreateRoleRequest req) {
    Role role = roleRepository.findById(id).orElseThrow();
    role.setName(req.name());
    role.setPermissions(new HashSet<>(permissionRepository.findAllById(req.permissionIds())));
    return toDto(roleRepository.save(role));
  }

  @Transactional
  public void delete(Long id) {
    Role role = roleRepository.findById(id).orElseThrow(RoleNotFoundException::new);
    if (userRepository.existsByRoles_Id(id)) {
      throw new RoleInUseException();
    }
    roleRepository.delete(role);
  }

  public static class RoleInUseException extends RuntimeException {
    public RoleInUseException() {
      super("Este rol está asignado a usuarios. Retíralo de sus cuentas antes de eliminarlo.");
    }
  }

  public static class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException() {
      super("Este rol ya no existe. Actualiza la lista de roles.");
    }
  }

  private RoleDto toDto(Role r) {
    return new RoleDto(
        r.getId(), r.getName(), r.getPermissions().stream().map(Permission::getName).toList());
  }
}
