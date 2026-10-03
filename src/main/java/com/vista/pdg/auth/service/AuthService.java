package com.vista.pdg.auth.service;

import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.service.CourseService;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.LoginRequest;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.dto.RegistrationIntent;
import com.vista.pdg.auth.dto.VerificationResponse;
import com.vista.pdg.auth.entity.Role;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.RoleRepository;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.exception.EmailAlreadyUsedException;
import com.vista.pdg.exception.RegistrationValidationException;
import com.vista.pdg.exception.TokenReuseDetectedException;
import com.vista.pdg.exception.VerificationException;
import com.vista.pdg.exception.VerificationRateException;
import com.vista.pdg.security.JwtTokenProvider;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final CourseService courseService;
  private final EmailVerificationService verification;
  private final VerificationMailer mailer;
  private final Clock clock;

  @Value("${auth.allowed-email-domains:}")
  private String allowedEmailDomains;

  @Value("${auth.min-password-length:8}")
  private int minPasswordLength;

  @Transactional(noRollbackFor = VerificationException.class)
  public AuthResponse register(RegisterRequest req) {
    Course course = validateIntent(req.intent());
    String email = normalize(req.email());
    // El duplicado conserva el contrato anterior; ningún registro nuevo omite la verificación.
    if (userRepository.existsByEmail(email))
      throw new EmailAlreadyUsedException("Ya existe una cuenta con ese correo");
    verification.consume(email, req.verificationId(), req.verificationCode());

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
                .course(course)
                .enabled(true)
                .emailVerifiedAt(clock.instant())
                .build());

    return issueFor(user);
  }

  public VerificationResponse requestVerification(RegistrationIntent req) {
    validateIntent(req);
    if (userRepository.existsByEmail(normalize(req.email())))
      throw new EmailAlreadyUsedException("Ya existe una cuenta con ese correo");
    mailer.checkConfigured();
    try {
      return verification.send(normalize(req.email()));
    } catch (DataIntegrityViolationException ex) {
      // Carrera de primera inserción: no enviar duplicados ni exponer restricciones SQL.
      throw new VerificationRateException(1);
    }
  }

  private Course validateIntent(RegistrationIntent req) {
    if (!req.password().equals(req.confirmPassword()))
      throw new RegistrationValidationException("confirmPassword", "Las contraseñas no coinciden");
    if (req.password().length() < minPasswordLength)
      throw new RegistrationValidationException(
          "password", "La contraseña debe tener al menos " + minPasswordLength + " caracteres");
    requireAllowedDomain(normalize(req.email()));
    return courseService.requireEnrollable(req.courseCode());
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
        user.getRoles().stream().map(Role::getName).sorted().toList(),
        user.getCourse() != null ? user.getCourse().getCode() : null,
        user.getCourse() != null ? user.getCourse().getTerm().getCode() : null);
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
      // Literal fijado por CA-2 de la HU-16: el E2E lo compara palabra por palabra.
      throw new RegistrationValidationException(
          "email", "Debes registrarte con tu correo institucional Icesi");
    }
  }

  private String normalize(String email) {
    return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
  }
}
