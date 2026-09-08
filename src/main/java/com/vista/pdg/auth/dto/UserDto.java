package com.vista.pdg.auth.dto;

import java.util.List;

public record UserDto(
    Long id, String displayName, String email, boolean enabled, List<String> roles) {}
