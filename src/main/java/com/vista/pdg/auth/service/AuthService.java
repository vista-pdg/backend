package com.vista.pdg.auth.service;

import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.dto.LoginResponse;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwt;

  public LoginResponse login(LoginRequest req) {
    User user =
        userRepository
            .findByEmail(req.email())
            .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

    if (!user.isEnabled()) throw new DisabledException("Usuario deshabilitado");

    if (!passwordEncoder.matches(req.password(), user.getPassword()))
      throw new BadCredentialsException("Credenciales incorrectas");

    String token = jwt.generate(user.getEmail(), user.getAuthorities());
    return new LoginResponse(
        token,
        user.getEmail(),
        user.getDisplayName(),
        user.getRoles().stream().map(Role::getName).toList());
  }
}
