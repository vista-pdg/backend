package com.vista.pdg.exception;

import com.vista.pdg.model.response.StructureResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  // ── Autenticación y registro ────────────────────────────────────────────

  /** Errores de Bean Validation: se devuelven por campo para que el formulario los sitúe. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new HashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    return ResponseEntity.unprocessableEntity()
        .body(ApiError.field("VALIDATION_ERROR", "Revisa los datos del formulario", fields));
  }

  @ExceptionHandler(RegistrationValidationException.class)
  public ResponseEntity<ApiError> handleRegistrationValidation(RegistrationValidationException ex) {
    return ResponseEntity.unprocessableEntity()
        .body(
            ApiError.field(
                "VALIDATION_ERROR", ex.getMessage(), Map.of(ex.field(), ex.getMessage())));
  }

  @ExceptionHandler(EmailAlreadyUsedException.class)
  public ResponseEntity<ApiError> handleEmailTaken(EmailAlreadyUsedException ex) {
    return ResponseEntity.status(409)
        .body(ApiError.field("EMAIL_TAKEN", ex.getMessage(), Map.of("email", ex.getMessage())));
  }

  /**
   * Reuso de token: la familia ya quedó revocada. Se responde 401 con un código propio para que el
   * frontend distinga «tu sesión caducó» de «alguien más usó tu token» y limpie el almacenamiento
   * local en vez de reintentar el refresco.
   */
  @ExceptionHandler(TokenReuseDetectedException.class)
  public ResponseEntity<ApiError> handleTokenReuse(TokenReuseDetectedException ex) {
    return ResponseEntity.status(401).body(ApiError.of("TOKEN_REUSE_DETECTED", ex.getMessage()));
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ResponseEntity<ApiError> handleInvalidRefresh(InvalidRefreshTokenException ex) {
    return ResponseEntity.status(401).body(ApiError.of("INVALID_REFRESH_TOKEN", ex.getMessage()));
  }

  @ExceptionHandler(DisabledException.class)
  public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
    return ResponseEntity.status(403).body(ApiError.of("USER_DISABLED", ex.getMessage()));
  }

  @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
  public ResponseEntity<ApiError> handleBadCredentials(AuthenticationException ex) {
    return ResponseEntity.status(401).body(ApiError.of("BAD_CREDENTIALS", ex.getMessage()));
  }

  // ── Generación de estructuras ───────────────────────────────────────────

  @ExceptionHandler(InvalidContractException.class)
  public ResponseEntity<StructureResponse> handleInvalidContract(InvalidContractException ex) {
    return ResponseEntity.status(HttpStatusCode.valueOf(422))
        .body(StructureResponse.error(ex.getMessage(), null));
  }

  @ExceptionHandler(LlmExhaustedException.class)
  public ResponseEntity<StructureResponse> handleLlmExhausted(LlmExhaustedException ex) {
    return ResponseEntity.status(503).body(StructureResponse.error(ex.getMessage(), ex.attempts()));
  }

  @ExceptionHandler(UnsupportedStructureException.class)
  public ResponseEntity<StructureResponse> handleUnsupported(UnsupportedStructureException ex) {
    return ResponseEntity.badRequest().body(StructureResponse.error(ex.getMessage(), null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<StructureResponse> handleGeneric(Exception ex) {
    return ResponseEntity.internalServerError()
        .body(StructureResponse.error("Unexpected error: " + ex.getMessage(), null));
  }
}
