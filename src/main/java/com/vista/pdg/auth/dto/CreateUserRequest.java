package com.vista.pdg.auth.dto;

import java.util.List;

public record CreateUserRequest(
    String displayName, String email, String password, List<Long> roleIds) {}
