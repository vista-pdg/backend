package com.vista.pdg.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Responde 401 cuando la petición llega sin autenticar.
 *
 * <p>Sin esto Spring Security devuelve 403 tanto al que no presenta token como al que lo presenta y
 * no tiene el rol, y son dos situaciones que el cliente debe tratar distinto: la primera se arregla
 * refrescando el token, la segunda no se arregla de ninguna manera. Con ambas colapsadas en 403 el
 * refresco silencioso del frontend nunca se disparaba, y una sesión con el acceso caducado sacaba
 * al usuario en lugar de renovarse sola.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiError.of("UNAUTHENTICATED", "Se requiere autenticación para acceder a este recurso"));
  }
}
