package com.vista.pdg.exception;

import com.vista.pdg.model.response.StructureResponse;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

  // ── Cuota y límite de tasa del asistente (HU-17) ────────────────────────

  @ExceptionHandler(DailyQuotaExceededException.class)
  public ResponseEntity<ApiError> handleDailyQuota(DailyQuotaExceededException ex) {
    return ResponseEntity.status(429)
        .header("X-Quota-Remaining", "0")
        .header("X-Quota-Reset", ex.resetsAt().toString())
        .body(ApiError.of("DAILY_QUOTA_EXCEEDED", ex.getMessage()));
  }

  /** {@code Retry-After} en segundos: es lo que el cliente usa para la cuenta regresiva (CA-3). */
  @ExceptionHandler(RateLimitedException.class)
  public ResponseEntity<ApiError> handleRateLimited(RateLimitedException ex) {
    return ResponseEntity.status(429)
        .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
        .body(ApiError.of("RATE_LIMITED", ex.getMessage()));
  }

  @ExceptionHandler(
      com.vista.pdg.assistant.service.CourseQuotaService.CourseNotFoundException.class)
  public ResponseEntity<ApiError> handleCourseNotFound(RuntimeException ex) {
    return ResponseEntity.status(404).body(ApiError.of("COURSE_NOT_FOUND", ex.getMessage()));
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

  /**
   * Créditos, cuota o clave del proveedor: 503 con el motivo, sin reintentos ni detalle interno.
   */
  @ExceptionHandler(LlmUnavailableException.class)
  public ResponseEntity<StructureResponse> handleLlmUnavailable(LlmUnavailableException ex) {
    return ResponseEntity.status(503).body(StructureResponse.error(ex.getMessage(), null));
  }

  @ExceptionHandler(UnsupportedStructureException.class)
  public ResponseEntity<StructureResponse> handleUnsupported(UnsupportedStructureException ex) {
    return ResponseEntity.badRequest().body(StructureResponse.error(ex.getMessage(), null));
  }

  // ── Peticiones mal formadas ─────────────────────────────────────────────

  /**
   * El cuerpo no se pudo leer: JSON roto, un campo primitivo ausente o nulo, un número donde iba
   * texto. Es un error de quien llama, no nuestro, así que 400 y no 500, y con el campo culpable
   * señalado para que se pueda corregir sin adivinar.
   *
   * <p>Se nombra el campo, nunca el tipo Java ni la excepción: al otro lado hay un cliente HTTP, no
   * alguien depurando nuestro código.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
    String field = offendingField(ex);
    String message =
        field == null
            ? "El cuerpo de la petición no se pudo leer: revisa que sea JSON válido"
            : "El campo «" + field + "» falta o no tiene un valor válido";
    log.debug("Cuerpo ilegible en la petición ({}): {}", ex.getCause(), ex.getMessage());
    return ResponseEntity.badRequest()
        .body(
            field == null
                ? ApiError.of("MALFORMED_REQUEST", message)
                : ApiError.field("MALFORMED_REQUEST", message, Map.of(field, message)));
  }

  /**
   * Ruta del campo que Jackson no pudo leer, en notación {@code nodes[0].x}.
   *
   * <p>Se recorre la cadena de causas en vez de mirar sólo la inmediata: quién envuelve a quién
   * depende del convertidor de mensajes, y una capa de más no debería dejar al cliente sin saber
   * qué campo corregir.
   */
  private static String offendingField(HttpMessageNotReadableException ex) {
    JacksonException mapping = null;
    for (Throwable t = ex; t != null && mapping == null; t = t.getCause()) {
      if (t instanceof JacksonException candidate && !candidate.getPath().isEmpty()) {
        mapping = candidate;
      }
      if (t.getCause() == t) break;
    }
    if (mapping == null) return null;
    StringBuilder path = new StringBuilder();
    for (JacksonException.Reference ref : mapping.getPath()) {
      if (ref.getPropertyName() != null) {
        if (!path.isEmpty()) path.append('.');
        path.append(ref.getPropertyName());
      } else if (ref.getIndex() >= 0) {
        path.append('[').append(ref.getIndex()).append(']');
      }
    }
    return path.isEmpty() ? null : path.toString();
  }

  /**
   * Lo que no supimos clasificar. El mensaje de la excepción <b>no</b> viaja al cliente: puede
   * llevar la consulta SQL completa con nombres de columnas, y eso es un plano de la base de datos
   * regalado a cualquiera que provoque un fallo. Queda en el log del servidor, que es donde sirve.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<StructureResponse> handleGeneric(Exception ex) {
    log.error("Fallo no controlado atendiendo la petición", ex);
    return ResponseEntity.internalServerError()
        .body(StructureResponse.error("Error inesperado en el servidor", null));
  }
}
