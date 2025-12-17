package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.UserClearanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserClearanceServiceImpl implements UserClearanceService {

    private final UserRepository userRepository;
    private final UserTrainingStatusRepository userTrainingStatusRepository;
    private final TrainingRepository trainingRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note (The "Ladder" Algorithm):</b>
     * <ul>
     * <li><b>Sequential Progression:</b> The logic checks levels 1 through 4 sequentially.
     * If a user satisfies Level 1 but fails requirements for Level 2, the loop breaks immediately.
     * This implies a user cannot hold Level 3 access without also satisfying Level 2.</li>
     * <li><b>Validation Criteria:</b> A training counts as "satisfied" only if:
     * <ol>
     * <li>Status is {@link ProgressStatus#COMPLETED}.</li>
     * <li>It is not expired ({@code validUntil > now}).</li>
     * <li>It has not been manually revoked ({@code isPassRevoked == false}).</li>
     * </ol>
     * </li>
     * <li><b>Empty Levels:</b> If a specific security level has no assigned trainings in the system,
     * it is considered "automatically passed" (pass-through).</li>
     * <li><b>Optimisation:</b> Database update and audit log occur only if the calculated level
     * is different from the current one.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public void recalculateUserClearance(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int newClearance = 0;

        // Fetch all user statuses at once to avoid N+1 queries inside the loop
        List<UserTrainingStatus> userStatuses = userTrainingStatusRepository.findByUserId(userId);

        for (int level = 1; level <= 4; level++) {
            List<Training> requiredTrainings = trainingRepository.findAllBySecurityLevel(level);

            // Edge case: No trainings defined for this level -> Auto Pass
            if (requiredTrainings.isEmpty()) {
                newClearance = level;
                continue;
            }

            boolean levelPassed = true;
            for (Training reqTraining : requiredTrainings) {
                boolean completed = userStatuses.stream().anyMatch(status ->
                        status.getTraining().getId().equals(reqTraining.getId()) &&
                                status.getStatus() == ProgressStatus.COMPLETED &&
                                (status.getValidUntil() == null || status.getValidUntil().isAfter(LocalDateTime.now(clock))) &&
                                !status.isPassRevoked()
                );

                if (!completed) {
                    levelPassed = false;
                    break;
                }
            }

            if (levelPassed) {
                newClearance = level;
            } else {
                // Break the ladder: if you fail Level X, you cannot achieve Level X+1
                break;
            }
        }

        if (user.getClearanceLevel() != newClearance) {
            int oldLevel = user.getClearanceLevel();
            user.setClearanceLevel(newClearance);
            userRepository.save(user);
            auditLogService.logEvent(user.getId(), "CLEARANCE_CHANGED",
                    "Security clearance changed from Level " + oldLevel + " to Level " + newClearance);
        }
    }
}