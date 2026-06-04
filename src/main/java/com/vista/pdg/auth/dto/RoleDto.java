package com.vista.pdg.auth.dto;

import java.util.List;

public record RoleDto(Long id, String name, List<String> permissions) {}
