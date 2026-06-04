package com.vista.pdg.auth.controller;

import com.vista.pdg.auth.dto.PermissionDto;
import com.vista.pdg.auth.repository.PermissionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
public class PermissionAdminController {

  private final PermissionRepository permissionRepository;

  @GetMapping
  public List<PermissionDto> list() {
    return permissionRepository.findAll().stream()
        .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
        .toList();
  }
}
