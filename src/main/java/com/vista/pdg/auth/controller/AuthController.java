package com.vista.pdg.auth.controller;

import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.dto.RefreshRequest;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    return ResponseEntity.ok(authService.refresh(req.refreshToken()));
  }

  /**
   * Cierra la sesión revocando la familia del token presentado. Es idempotente a propósito: un
   * logout con un token ya revocado responde 204 igual, porque el resultado que el cliente pide
   * —que la sesión no sirva— ya se cumple.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
    authService.logout(req.refreshToken());
    return ResponseEntity.noContent().build();
  }
}
