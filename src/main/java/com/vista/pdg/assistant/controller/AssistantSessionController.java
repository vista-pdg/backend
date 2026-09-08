package com.vista.pdg.assistant.controller;

import com.vista.pdg.assistant.session.AssistantSessionService;
import com.vista.pdg.assistant.session.SessionStatus;
import com.vista.pdg.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sesión de trabajo del asistente (HU-32). El cliente la consulta para mostrar cuánto le queda y la
 * borra cuando el estudiante limpia el lienzo.
 */
@RestController
@RequestMapping("/api/assistant/session")
@RequiredArgsConstructor
public class AssistantSessionController {

  private final AssistantSessionService sessions;

  @GetMapping
  public SessionStatus status(@AuthenticationPrincipal User user) {
    return sessions.status(user.getId());
  }

  /** 204 aunque no hubiera sesión: borrar lo que ya no está es un éxito, no un error. */
  @DeleteMapping
  public ResponseEntity<Void> clear(@AuthenticationPrincipal User user) {
    sessions.clear(user.getId());
    return ResponseEntity.noContent().build();
  }
}
