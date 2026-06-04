package com.vista.pdg.auth.controller;

import com.vista.pdg.auth.dto.AssignRolesRequest;
import com.vista.pdg.auth.dto.CreateUserRequest;
import com.vista.pdg.auth.dto.UserDto;
import com.vista.pdg.auth.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

  private final UserService userService;

  @GetMapping
  public List<UserDto> list() {
    return userService.findAll();
  }

  @PostMapping
  public UserDto create(@RequestBody CreateUserRequest req) {
    return userService.create(req);
  }

  @PutMapping("/{id}")
  public UserDto update(@PathVariable Long id, @RequestBody CreateUserRequest req) {
    return userService.update(id, req);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    userService.delete(id);
  }

  @PutMapping("/{id}/roles")
  public UserDto assignRoles(@PathVariable Long id, @RequestBody AssignRolesRequest req) {
    return userService.assignRoles(id, req.roleIds());
  }
}
