package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.dto.verification.VerificationResponse;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.TrainingService;
import com.fvps.backend.services.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private final UserRepository userRepository;
    private final TrainingService trainingService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public VerificationResponse verifyUserAccess(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return VerificationResponse.builder()
                    .accessGranted(false)
                    .message(AppMessage.ACCESS_DENIED_USER_NOT_FOUND.name())
                    .build();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditLogService.logEvent(user.getId(), "ACCESS_DENIED", "Access denied: Account inactive or blocked.");
            return VerificationResponse.builder()
                    .accessGranted(false)
                    .message(AppMessage.ACCESS_DENIED_BLOCKED.name())
                    .fullName(user.getName() + " " + user.getSurname())
                    .photoUrl(user.getPhotoUrl())
                    .build();
        }

        List<UserTrainingDto> validTrainings = trainingService.getValidTrainingsForUser(userId);

        if (validTrainings.isEmpty()) {
            auditLogService.logEvent(user.getId(), "ACCESS_DENIED", "Access denied: No valid trainings found.");
            return VerificationResponse.builder()
                    .accessGranted(false)
                    .message(AppMessage.ACCESS_DENIED_NO_TRAINING.name())
                    .fullName(user.getName() + " " + user.getSurname())
                    .photoUrl(user.getPhotoUrl())
                    .build();
        }

        auditLogService.logEvent(user.getId(), "ACCESS_GRANTED", "Access granted. Valid trainings count: " + validTrainings.size());
        return VerificationResponse.builder()
                .accessGranted(true)
                .message(AppMessage.ACCESS_GRANTED.name())
                .fullName(user.getName() + " " + user.getSurname())
                .photoUrl(user.getPhotoUrl())
                .build();
    }
}