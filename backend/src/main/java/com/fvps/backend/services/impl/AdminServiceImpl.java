package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final TrainingService trainingService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final PdfGeneratorService pdfGeneratorService;
    private final PassService passService;

    @Override
    public void changeUserStatus(UUID userId, UserStatus status) {
        userService.changeUserStatus(userId, status);
    }

    @Override
    public void deleteUser(UUID id) {
        userService.deleteUser(id);
    }

    @Override
    @Transactional
    public byte[] generatePassPdf(UUID userId) {
        var user = userService.getById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Cannot generate pass for inactive/blocked user.");
        }
        var validTrainings = trainingService.getValidTrainingsForUser(userId);

        byte[] pdf = pdfGeneratorService.generatePassPdf(user, validTrainings);
        auditLogService.logEvent(userId, "PASS_PREVIEWED", "Admin previewed pass.");
        return pdf;
    }

    @Override
    @Transactional
    public void resendPassEmail(UUID userId) {
        User user = userService.getById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User is not active.");
        }

        var validTrainings = trainingService.getValidTrainingsForUser(userId);

        passService.sendPass(user, validTrainings);

        auditLogService.logEvent(userId, "PASS_RESENT_BY_ADMIN", "Admin forced pass resend.");
    }
}