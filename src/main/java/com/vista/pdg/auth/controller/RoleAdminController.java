package com.vista.pdg.auth.controller;

import com.vista.pdg.auth.dto.CreateRoleRequest;
import com.vista.pdg.auth.dto.RoleDto;
import com.vista.pdg.auth.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleAdminController {

  private final RoleService roleService;

  @GetMapping
  public List<RoleDto> list() {
    return roleService.findAll();
  }

  @PostMapping
  public RoleDto create(@RequestBody CreateRoleRequest req) {
    return roleService.create(req);
  }

  @PutMapping("/{id}")
  public RoleDto update(@PathVariable Long id, @RequestBody CreateRoleRequest req) {
    return roleService.update(id, req);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    roleService.delete(id);
  }
}
