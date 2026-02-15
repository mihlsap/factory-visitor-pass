package com.fvps.backend.services;

import com.fvps.backend.domain.entities.User;

/**
 * Service responsible for managing communication regarding Visitor Passes.
 * <p>
 * This service handles the "human-facing" side of the security clearance process,
 * alerting users when they have completed training or when their access status changes.
 * </p>
 */
public interface PassService {

    /**
     * Notifies the user that they have successfully completed required training
     * and their Visitor Pass is updated.
     *
     * @param user           the user who completed the training.
     */
    void sendPassCompletionNotification(User user);

    /**
     * Notifies the user about an administrative change to their account status.
     * <p>
     * Crucial for transparency, ensuring users know why they might suddenly lose access
     * (e.g. "Blocked due to safety violation") or when access is restored.
     * </p>
     *
     * @param user   the affected user.
     * @param reason the explanation text (admin-provided or system-generated).
     */
    void sendStatusChangeNotification(User user, String reason);
}