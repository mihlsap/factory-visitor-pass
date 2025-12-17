package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.verification.VerificationResponse;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.UserClearanceService;
import com.fvps.backend.services.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private final UserRepository userRepository;
    private final UserClearanceService userClearanceService;
    private final AuditLogService auditLogService;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note (Security):</b>
     * <ul>
     * <li><b>Just-In-Time Recalculation:</b> Before making a decision, this method calls
     * {@link UserClearanceService#recalculateUserClearance(UUID)}. This forces a fresh check
     * of all expiration dates. Even if the background scheduler hasn't run yet, an expired
     * training will be detected <i>at the moment of scan</i>, preventing unauthorised access.</li>
     * <li><b>Audit Trail:</b> Every scan attempt (successful or failed) is logged.
     * This creates a movement history for the user within the facility.</li>
     * <li><b>Visual Verification:</b> Returns the user's name and photo URL even if access is denied
     * (unless the user is not found), allowing the guard to verify if the person holding the pass is the owner.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public VerificationResponse verifyUserAccess(UUID userId, int requiredLevel) {
        User user = userRepository.findById(userId).orElse(null);

        // Scenario 1: QR Code is invalid or user deleted
        if (user == null) {
            return VerificationResponse.builder()
                    .accessGranted(false)
                    .message(AppMessage.ACCESS_DENIED_USER_NOT_FOUND.name())
                    .build();
        }

        // Scenario 2: User exists but is blocked/inactive
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditLogService.logEvent(user.getId(), "ACCESS_DENIED", "Access denied: Account inactive.");
            return VerificationResponse.builder()
                    .accessGranted(false)
                    .message(AppMessage.ACCESS_DENIED_BLOCKED.name())
                    .fullName(user.getName() + " " + user.getSurname())
                    .photoUrl(user.getPhotoUrl())
                    .build();
        }

        // Force fresh calculation to ensure no stale data is used
        userClearanceService.recalculateUserClearance(userId);

        // Reload the user to get the calculated level
        user = userRepository.findById(userId).orElseThrow();

        // Scenario 3: Check levels
        if (user.getClearanceLevel() >= requiredLevel) {
            auditLogService.logEvent(user.getId(), "ACCESS_GRANTED",
                    "Access granted. Required: " + requiredLevel + ", Has: " + user.getClearanceLevel());

            return VerificationResponse.builder()
                    .accessGranted(true)
                    .message(AppMessage.ACCESS_GRANTED.name())
                    .fullName(user.getName() + " " + user.getSurname())
                    .photoUrl(user.getPhotoUrl())
                    .build();
        } else {
            auditLogService.logEvent(user.getId(), "ACCESS_DENIED",
                    "Insufficient clearance. Required: " + requiredLevel + ", Has: " + user.getClearanceLevel());

            return VerificationResponse.builder()
                    .accessGranted(false)
                    .message(AppMessage.ACCESS_DENIED_LOW_LEVEL.name())
                    .fullName(user.getName() + " " + user.getSurname())
                    .photoUrl(user.getPhotoUrl())
                    .build();
        }
    }
}