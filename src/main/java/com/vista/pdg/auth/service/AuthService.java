package com.vista.pdg.auth.service;

import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.exception.EmailAlreadyUsedException;
import com.vista.pdg.exception.RegistrationValidationException;
import com.vista.pdg.exception.TokenReuseDetectedException;
import com.vista.pdg.security.JwtTokenProvider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  /**
   * Rol con el que nace toda cuenta autoregistrada. TEACHER y ADMIN los asigna un administrador.
   */
  public static final String DEFAULT_ROLE = "STUDENT";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwt;
  private final RefreshTokenService refreshTokenService;

  @Value("${auth.allowed-email-domains:}")
  private String allowedEmailDomains;

  @Value("${auth.min-password-length:8}")
  private int minPasswordLength;

  @Transactional
  public AuthResponse register(RegisterRequest req) {
    String email = normalize(req.email());

    if (!req.password().equals(req.confirmPassword())) {
      throw new RegistrationValidationException("confirmPassword", "Las contraseñas no coinciden");
    }
    if (req.password().length() < minPasswordLength) {
      throw new RegistrationValidationException(
          "password", "La contraseña debe tener al menos " + minPasswordLength + " caracteres");
    }
    requireAllowedDomain(email);
    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyUsedException("Ya existe una cuenta con ese correo");
    }

    Role studentRole =
        roleRepository
            .findByName(DEFAULT_ROLE)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El rol " + DEFAULT_ROLE + " no existe. Revisa el seeder."));

    User user =
        userRepository.save(
            User.builder()
                .displayName(req.displayName().strip())
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                // Mutable a propósito: el seeder reasigna roles y no puede toparse con Set.of().
                .roles(new HashSet<>(Set.of(studentRole)))
                .enabled(true)
                .build());

    return issueFor(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest req) {
    User user =
        userRepository
            .findByEmail(normalize(req.email()))
            .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

    if (!user.isEnabled()) throw new DisabledException("Usuario deshabilitado");

    if (!passwordEncoder.matches(req.password(), user.getPassword())) {
      throw new BadCredentialsException("Credenciales incorrectas");
    }

    return issueFor(user);
  }

  /**
   * Rota el refresco y emite un acceso nuevo. La detección de reuso vive en el servicio de tokens.
   *
   * <p>Repite el {@code noRollbackFor} de {@code RefreshTokenService.rotate}: al propagarse
   * REQUIRED ambas anotaciones actúan sobre la misma transacción física, y basta con que este nivel
   * aplique la regla por defecto para que la revocación de la familia se pierda al hacer rollback.
   */
  @Transactional(noRollbackFor = TokenReuseDetectedException.class)
  public AuthResponse refresh(String rawRefreshToken) {
    RefreshTokenService.IssuedToken rotated = refreshTokenService.rotate(rawRefreshToken);
    User user = rotated.entity().getUser();
    return buildResponse(user, rotated.rawToken());
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenService.revokeFamilyOf(rawRefreshToken);
  }

  private AuthResponse issueFor(User user) {
    RefreshTokenService.IssuedToken issued = refreshTokenService.issueNewFamily(user);
    return buildResponse(user, issued.rawToken());
  }

  private AuthResponse buildResponse(User user, String rawRefreshToken) {
    return new AuthResponse(
        jwt.generateAccessToken(user.getEmail(), user.getAuthorities()),
        rawRefreshToken,
        "Bearer",
        jwt.accessTokenTtlSeconds(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRoles().stream().map(Role::getName).sorted().toList());
  }

  /**
   * El proyecto es institucional, así que el registro abierto se limita a los dominios de Icesi. La
   * lista es configurable y, si se deja vacía, no se aplica ninguna restricción.
   */
  private void requireAllowedDomain(String email) {
    if (allowedEmailDomains == null || allowedEmailDomains.isBlank()) return;

    List<String> domains =
        Arrays.stream(allowedEmailDomains.split(","))
            .map(d -> d.strip().toLowerCase(Locale.ROOT))
            .filter(d -> !d.isEmpty())
            .toList();

    boolean allowed = domains.stream().anyMatch(d -> email.endsWith("@" + d));
    if (!allowed) {
      throw new RegistrationValidationException(
          "email", "Usa tu correo institucional @" + domains.getFirst());
    }
  }

  private String normalize(String email) {
    return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
  }
}
