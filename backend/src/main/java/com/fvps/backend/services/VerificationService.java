package com.fvps.backend.services;

import com.fvps.backend.domain.dto.verification.VerificationResponse;

import java.util.UUID;

public interface VerificationService {
    VerificationResponse verifyUserAccess(UUID userId);
}