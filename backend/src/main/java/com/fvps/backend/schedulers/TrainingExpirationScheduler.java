package com.fvps.backend.schedulers;

import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Background job responsible for monitoring training validity periods.
 * <p>
 * This scheduler runs automatically at a configured time to identify users
 * whose training certifications
 * are approaching their expiration date. It sends proactive email notifications
 * to ensure users
 * renew their training before losing access to the facility.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingExpirationScheduler {

    private final UserTrainingStatusRepository statusRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final MessageSource messageSource;
    private final Locale defaultLocale;

    /**
     * Main scheduled task entry point.
     * <p>
     * Executed based on the cron expression defined in
     * {@code app.scheduler.expiration-cron}.
     * Default configuration usually runs this once a day (e.g. at 18:00).
     * </p>
     * <p>
     * The task performs two checks:
     * <ol>
     * <li><b>7-day warning:</b> Reminds users that they have a week left.</li>
     * <li><b>1-day urgent notice:</b> Final reminder before the pass expires
     * tomorrow.</li>
     * </ol>
     * </p>
     */
    @Scheduled(cron = "${app.scheduler.expiration-cron:0 0 18 * * *}")
    @Transactional(readOnly = true)
    public void notifyUpcomingExpirations() {
        log.info("Starting training expiration check...");

        // Check for expiration in exactly 7 days
        checkAndNotify(7);

        // Check for expiration in exactly 1 day
        checkAndNotify(1);

        log.info("Training expiration check completed.");
    }

    /**
     * Executed based on the cron expression defined in
     * {@code app.scheduler.expired-cron}.
     * Default configuration usually runs this once a day (e.g. at 3:00).
     * <p>
     * Scans for trainings that have officially expired and updates their status to
     * EXPIRED.
     * This separates "notification" logic from actual "state change" logic.
     * </p>
     */
    @Scheduled(cron = "${app.scheduler.expired-cron:0 30 22 * * *}")
    @Transactional
    public void expireTrainings() {
        log.info("Starting daily training expiration job...");

        LocalDateTime now = LocalDateTime.now(clock);
        List<UserTrainingStatus> expiredList = statusRepository.findAllByStatusAndValidUntilBefore(
                ProgressStatus.COMPLETED, now);

        if (expiredList.isEmpty()) {
            log.info("No expired trainings found.");
            return;
        }

        log.info("Found {} trainings to expire.", expiredList.size());

        for (UserTrainingStatus status : expiredList) {
            try {
                status.setStatus(ProgressStatus.EXPIRED);
                statusRepository.save(status);

                auditLogService.logEvent(
                        status.getUser().getId(),
                        "TRAINING_EXPIRED",
                        "Training automatically expired: " + status.getTraining().getTitle());

                sendExpiredEmail(status);
            } catch (Exception e) {
                log.error("Failed to expire training {} for user {}", status.getTraining().getId(),
                        status.getUser().getId(), e);
            }
        }

        log.info("Training expiration job completed.");
    }

    /**
     * Queries the database for trainings expiring in a specific number of days and
     * triggers notifications.
     *
     * @param daysInAdvance the number of days from "now" to check (e.g. 7 or 1).
     */
    private void checkAndNotify(int daysInAdvance) {
        // Calculate the time range for that specific day (start of day to the end of
        // day)
        LocalDateTime start = LocalDateTime.now(clock).plusDays(daysInAdvance).with(LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now(clock).plusDays(daysInAdvance).with(LocalTime.MAX);

        List<UserTrainingStatus> expiringStatuses = statusRepository.findAllByStatusAndValidUntilBetween(
                ProgressStatus.COMPLETED, start, end);

        if (expiringStatuses.isEmpty()) {
            return;
        }

        log.info("Found {} trainings expiring in {} days.", expiringStatuses.size(), daysInAdvance);

        for (UserTrainingStatus status : expiringStatuses) {
            try {
                // Isolated try-catch block ensures that one failed email doesn't stop the
                // entire batch
                sendNotificationEmail(status, daysInAdvance);
            } catch (Exception e) {
                log.error("Failed to send expiration email to user: {}", status.getUser().getId(), e);
            }
        }
    }

    /**
     * Constructs and sends the localised expiration warning email.
     * <p>
     * Also records an audit log entry to prove that the system attempted to notify
     * the user.
     * </p>
     *
     * @param status   the training status record containing user and training
     *                 details.
     * @param daysLeft the number of days remaining until expiration.
     */
    private void sendNotificationEmail(UserTrainingStatus status, int daysLeft) {
        String email = status.getUser().getEmail();
        String name = status.getUser().getName();
        String trainingTitle = status.getTraining().getTitle();

        // Fetch localised messages
        String subject = messageSource.getMessage("email.expiration.subject", null, defaultLocale);

        Object[] args = { name, trainingTitle, daysLeft };
        String content = messageSource.getMessage("email.expiration.body", args, defaultLocale);

        emailService.sendEmail(email, subject, content);

        auditLogService.logEvent(status.getUser().getId(), "NOTIFICATION_SENT",
                "Expiration warning sent (" + daysLeft + " days left) for training: " + trainingTitle);
    }

    private void sendExpiredEmail(UserTrainingStatus status) {
        String email = status.getUser().getEmail();
        String name = status.getUser().getName();
        String trainingTitle = status.getTraining().getTitle();

        String subject = messageSource.getMessage("email.expired.subject", null, defaultLocale);

        Object[] args = { name, trainingTitle };
        String content = messageSource.getMessage("email.expired.body", args, defaultLocale);

        emailService.sendEmail(email, subject, content);

        auditLogService.logEvent(status.getUser().getId(), "NOTIFICATION_SENT",
                "Expiration notification sent for training: " + trainingTitle);
    }
}