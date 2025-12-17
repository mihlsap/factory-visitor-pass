package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.auth.ChangePasswordRequest;
import com.fvps.backend.domain.dto.user.UpdateUserRequest;
import com.fvps.backend.domain.dto.user.UserPassDto;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final PdfGeneratorService pdfGeneratorService;
    private final TrainingProgressService trainingProgressService;
    private final UserClearanceService userClearanceService;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final Locale defaultLocale;
    private final Clock clock;

    @Value("${app.auth.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.auth.lock-time-minutes}")
    private int lockTimeMinutes;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSummaryDto> getAllUsersSummary(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapUserToSummary);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * If the new status is {@link UserStatus#ACTIVE}, this method automatically resets
     * {@code failedLoginAttempts} and removes the {@code lockoutTime}. This allows admins
     * to manually unblock users who were locked out due to brute-force attempts.
     * </p>
     */
    @Override
    @Transactional
    public void changeUserStatus(UUID userId, UserStatus newStatus) {
        User user = getById(userId);
        UserStatus oldStatus = user.getStatus();

        if (oldStatus == newStatus) {
            throw new IllegalArgumentException("New status is the same as the current one.");
        }

        user.setStatus(newStatus);

        if (newStatus == UserStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
            user.setLockoutTime(null);
        }

        userRepository.save(user);

        auditLogService.logEvent(userId, "USER_STATUS_CHANGE",
                "Status changed from " + oldStatus + " to " + newStatus);

        sendAccountStatusEmail(user, newStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getUserSummaryByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return mapUserToSummary(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getUserSummaryById(UUID id) {
        User user = getById(id);
        return mapUserToSummary(user);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Optimistic Locking:</b> Throws {@link OptimisticLockingFailureException} if the version check fails.</li>
     * <li><b>File Handling:</b> If a new photo is provided, the old photo file is physically deleted from storage to prevent orphans.</li>
     * <li><b>Audit:</b> Logs distinct events for photo updates vs. data updates.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public void updateUserData(String email, UpdateUserRequest request, MultipartFile photo) {
        User user = userRepository.findByEmail(email).orElseThrow();
        boolean dataChanged = false;

        if (request.getVersion() != null && !request.getVersion().equals(user.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "User profile version mismatch. Client has version " + request.getVersion() +
                            " but DB has " + user.getVersion()
            );
        }

        if (!user.getName().equals(request.getName())) {
            user.setName(request.getName());
            dataChanged = true;
        }

        if (!user.getSurname().equals(request.getSurname())) {
            user.setSurname(request.getSurname());
            dataChanged = true;
        }

        if (request.getCompanyName() != null && !request.getCompanyName().equals(user.getCompanyName())) {
            user.setCompanyName(request.getCompanyName());
            dataChanged = true;
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            user.setPhoneNumber(request.getPhoneNumber());
            dataChanged = true;
        }

        if (photo != null && !photo.isEmpty()) {
            String oldPhotoFilename = user.getPhotoUrl();
            String newPhotoFilename = fileStorageService.savePhoto(photo);
            user.setPhotoUrl(newPhotoFilename);

            if (oldPhotoFilename != null) {
                fileStorageService.deletePhoto(oldPhotoFilename);
            }

            auditLogService.logEvent(user.getId(), "PHOTO_UPDATED", "Updated profile photo.");
            dataChanged = true;
        }

        if (dataChanged) {
            userRepository.save(user);
            auditLogService.logEvent(user.getId(), "PROFILE_UPDATE", "Updated profile details.");
        }
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditLogService.logEvent(user.getId(), "PASSWORD_CHANGE_FAILED", "Incorrect current password provided.");
            throw new RuntimeException("Current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        auditLogService.logEvent(user.getId(), "PASSWORD_CHANGED", "User changed their password.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * Annotated with {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}.
     * This ensures the increment of login attempts is committed in a <b>separate transaction</b>.
     * Even if the main login process throws an exception (which rolls back the main transaction),
     * this security counter update persists, preventing brute-force attacks.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailedLogin(UUID userId) {
        User user = getById(userId);

        int newAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newAttempts);

        if (newAttempts >= maxFailedAttempts) {
            user.setLockoutTime(LocalDateTime.now(clock).plusMinutes(lockTimeMinutes));
            auditLogService.logEvent(user.getId(), "ACCOUNT_LOCKED",
                    "Account locked after " + maxFailedAttempts + " failed attempts.");
        } else {
            auditLogService.logEvent(user.getId(), "LOGIN_FAILED",
                    "Failed login attempt (" + newAttempts + "/" + maxFailedAttempts + ").");
        }
        userRepository.save(user);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetLockoutStats(UUID userId) {
        User user = getById(userId);
        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
        userRepository.save(user);
        auditLogService.logEvent(user.getId(), "LOCKOUT_EXPIRED", "Temporary lockout expired. Counters reset.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * Forces a fresh recalculation of user clearance via {@link UserClearanceService} before generating the PDF.
     * This ensures that if a training expired 1 second ago, the generated pass will correctly reflect the downgraded status.
     * </p>
     */
    @Override
    public byte[] generateMyPassPdf(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Cannot generate pass. Your account is not active.");
        }

        // Force recalculation to ensure fresh data
        userClearanceService.recalculateUserClearance(user.getId());

        // Reload user to get updated clearance level
        user = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        var validTrainings = trainingProgressService.getValidTrainingsForUser(user.getId());

        if (validTrainings.isEmpty()) {
            throw new IllegalStateException("You have no valid, completed trainings. Please complete required trainings first.");
        }

        byte[] pdf = pdfGeneratorService.generatePassPdf(user, validTrainings);

        auditLogService.logEvent(user.getId(), "PASS_DOWNLOADED", "User downloaded their own pass.");
        return pdf;
    }

    @Override
    @Transactional
    public UserPassDto getUserPassData(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active.");
        }

        userClearanceService.recalculateUserClearance(user.getId());

        user = getById(user.getId());

        var validTrainings = trainingProgressService.getValidTrainingsForUser(user.getId());

        return UserPassDto.builder()
                .userId(user.getId())
                .fullName(user.getName() + " " + user.getSurname())
                .companyName(user.getCompanyName())
                .photoUrl(user.getPhotoUrl())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .clearanceLevel(user.getClearanceLevel())
                .qrCodeContent(user.getId().toString())
                .validTrainings(validTrainings)
                .build();
    }

    private UserSummaryDto mapUserToSummary(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .surname(user.getSurname())
                .companyName(user.getCompanyName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .photoUrl(user.getPhotoUrl())
                .version(user.getVersion())
                .clearanceLevel(user.getClearanceLevel())
                .build();
    }

    private void sendAccountStatusEmail(User user, UserStatus status) {
        String subject;
        String content;
        Object[] args;

        switch (status) {
            case BLOCKED -> {
                args = new Object[]{user.getName()};
                subject = messageSource.getMessage("email.account.blocked.subject", null, defaultLocale);
                content = messageSource.getMessage("email.account.blocked.body", args, defaultLocale);
            }
            case DELETED -> {
                args = new Object[]{user.getName()};
                subject = messageSource.getMessage("email.account.deleted.subject", null, defaultLocale);
                content = messageSource.getMessage("email.account.deleted.body", args, defaultLocale);
            }
            case ACTIVE -> {
                args = new Object[]{user.getName(), frontendUrl + "/login"};
                subject = messageSource.getMessage("email.account.activated.subject", null, defaultLocale);
                content = messageSource.getMessage("email.account.activated.body", args, defaultLocale);
            }
            default -> {
                return;
            }
        }

        emailService.sendEmail(user.getEmail(), subject, content);
    }
}