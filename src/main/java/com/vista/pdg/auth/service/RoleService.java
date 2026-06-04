package com.vista.pdg.auth.service;

import com.vista.pdg.auth.dto.CreateRoleRequest;
import com.vista.pdg.auth.dto.RoleDto;
import com.vista.pdg.auth.entity.Permission;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.repository.PermissionRepository;
import com.vista.pdg.auth.repository.RoleRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

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

  public void delete(Long id) {
    roleRepository.deleteById(id);
  }

  private RoleDto toDto(Role r) {
    return new RoleDto(
        r.getId(),
        r.getName(),
        r.getPermissions().stream().map(Permission::getName).toList());
  }
}
