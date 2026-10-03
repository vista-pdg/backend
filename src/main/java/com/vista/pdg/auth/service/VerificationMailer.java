package com.vista.pdg.auth.service;

public interface VerificationMailer {
  void checkConfigured();

  void send(String email, String code, long expiryMinutes);
}
