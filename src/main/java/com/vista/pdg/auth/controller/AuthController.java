package com.vista.pdg.auth.controller;

import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.dto.LoginResponse;
import com.vista.pdg.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req));
  }
}
