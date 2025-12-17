package com.fvps.backend.services;

import com.fvps.backend.domain.enums.UserStatus;

import java.util.UUID;

/**
 * Service providing administrative operations restricted to users with an ADMIN role.
 * <p>
 * This interface handles high-level administrative tasks such as user management
 * and overriding system rules (e.g. generating passes for inspection).
 * </p>
 */
public interface AdminService {

    /**
     * Updates the status of a specific user account.
     * <p>
     * Used to block users (revoke access) or unblock/restore access.
     * </p>
     *
     * @param userId the UUID of the user to update.
     * @param status the new {@link UserStatus} to apply.
     */
    void changeUserStatus(UUID userId, UserStatus status);

    /**
     * Generates a PDF preview of the Visitor Pass for a specific user.
     * <p>
     * This allows administrators to verify what the pass looks like or print it on behalf of the user.
     * The generation is subject to strict validation rules regarding the user's eligibility.
     * </p>
     *
     * @param userId the UUID of the user.
     * @return a byte array containing the generated PDF file.
     * @throws IllegalStateException if the user is not {@code ACTIVE} or has no valid, completed trainings.
     */
    byte[] generatePassPdf(UUID userId);
}