package com.fvps.backend.services;

import java.util.UUID;

/**
 * Service responsible for managing the user's security clearance level (0-4).
 * <p>
 * This service acts as a calculation engine. It does not manage the trainings themselves
 * but aggregates the user's current training status to determine their overall
 * access rights within the facility.
 * </p>
 */
public interface UserClearanceService {

    /**
     * Recalculates and updates the security level for a specific user.
     * <p>
     * This method should be triggered whenever a training is completed, expired,
     * revoked, or when training definitions change. It iterates through security levels
     * and assigns the highest level for which all requirements are met.
     * </p>
     *
     * @param userId the UUID of the user to evaluate.
     */
    void recalculateUserClearance(UUID userId);
}