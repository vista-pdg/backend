package com.vista.pdg.controller.dto;

public record GenerateRequest(String prompt, String type, String subtype) {
  public GenerateRequest(String prompt) {
    this(prompt, null, null);
  }
}
