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

    @Override
    @Transactional
    public void recalculateUserClearance(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int newClearance = 0;

        List<UserTrainingStatus> userStatuses = userTrainingStatusRepository.findByUserId(userId);

        for (int level = 1; level <= 4; level++) {
            List<Training> requiredTrainings = trainingRepository.findAllBySecurityLevel(level);

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