package com.fvps.backend.services.impl;

import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final TrainingProgressService trainingProgressService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final PdfGeneratorService pdfGeneratorService;

    @Override
    public void changeUserStatus(UUID userId, UserStatus status) {
        userService.changeUserStatus(userId, status);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li>Validates that the user status is strictly {@link UserStatus#ACTIVE}.</li>
     * <li>Verifies that the user has at least one valid training certificate.</li>
     * <li><b>Side Effect:</b> Logs an audit event {@code PASS_PREVIEWED} to record that an admin accessed this document.</li>
     * </ul>
     * </p>
     */
    @Override
    public byte[] generatePassPdf(UUID userId) {
        var user = userService.getById(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Cannot generate pass. User is not active (Status: " + user.getStatus() + ").");
        }

        var validTrainings = trainingProgressService.getValidTrainingsForUser(userId);

        if (validTrainings.isEmpty()) {
            throw new IllegalStateException("Cannot generate pass. User has no valid, completed trainings.");
        }

        byte[] pdf = pdfGeneratorService.generatePassPdf(user, validTrainings);
        auditLogService.logEvent(userId, "PASS_PREVIEWED", "Admin previewed pass.");
        return pdf;
    }

}