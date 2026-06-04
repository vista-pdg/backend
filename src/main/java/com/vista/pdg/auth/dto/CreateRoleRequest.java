package com.vista.pdg.auth.dto;

import java.util.List;

public record CreateRoleRequest(String name, List<Long> permissionIds) {}
