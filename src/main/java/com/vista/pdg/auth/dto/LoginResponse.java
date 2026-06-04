package com.vista.pdg.auth.dto;

import java.util.List;

public record LoginResponse(String token, String email, String displayName, List<String> roles) {}
