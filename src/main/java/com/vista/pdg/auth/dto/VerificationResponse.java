package com.vista.pdg.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record VerificationResponse(
    UUID verificationId, Instant expiresAt, Instant resendAvailableAt) {}
