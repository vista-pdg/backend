package com.vista.pdg.assistant.controller;

import com.vista.pdg.assistant.dto.QuotaStatus;
import com.vista.pdg.assistant.service.AssistantQuotaService;
import com.vista.pdg.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado de la cuota del usuario autenticado; el frontend lo pide al entrar (CA-5). */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

  private final AssistantQuotaService quotaService;

  @GetMapping("/quota")
  public QuotaStatus quota(@AuthenticationPrincipal User user) {
    return quotaService.status(user);
  }
}
