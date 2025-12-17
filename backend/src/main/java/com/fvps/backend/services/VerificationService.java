package com.fvps.backend.services;

import com.fvps.backend.domain.dto.verification.VerificationResponse;

import java.util.UUID;

/**
 * Service responsible for the final access control decision point.
 * <p>
 * This service is typically consumed by the mobile scanner app used by security guards.
 * It takes the user's identity (from a QR code) and the security requirement of the specific zone
 * to determine if entry should be allowed.
 * </p>
 */
public interface VerificationService {

    /**
     * Verifies if a user is authorised to enter a zone with a specific security level.
     * <p>
     * This operation performs a real-time check of the user's status, training validity,
     * and clearance level. It returns a detailed response to help the security guard
     * visually confirm the person's identity.
     * </p>
     *
     * @param userId        the UUID extracted from the user's QR code.
     * @param requiredLevel the minimum security clearance required for the zone (e.g. 1=Lobby, 4=Hazardous).
     * @return a {@link VerificationResponse} containing the decision (Granted/Denied), reason, and user profile data.
     */
    VerificationResponse verifyUserAccess(UUID userId, int requiredLevel);
}