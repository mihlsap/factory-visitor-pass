package com.fvps.backend.listeners;

import com.fvps.backend.events.UserStatusChangedEvent;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.PassService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener responsible for handling asynchronous notifications related to user status changes.
 * <p>
 * This component decouples the core domain logic (e.g. an Admin blocking a user via API) from
 * side effects like sending emails. It runs tasks in a separate thread and includes a retry mechanism
 * to handle temporary failures (e.g. SMTP server downtime) without affecting the user experience.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final PassService passService;
    private final AuditLogService auditLogService;

    /**
     * Handles the {@link UserStatusChangedEvent} asynchronously.
     * <p>
     * Triggers an email notification informing the user about their new account status
     * (e.g. "Account Blocked", "Access Restored").
     * </p>
     * <p>
     * <b>Retry Logic:</b> If the notification fails (e.g. network error), the operation is retried
     * up to 5 times. The delay between attempts is configurable via {@code app.notification.retry-delay}
     * (default: 10 seconds).
     * </p>
     *
     * @param event the event object containing the affected user and the reason for the change.
     */
    @Async
    @EventListener
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 5,
            backoff = @Backoff(delayExpression = "${app.notification.retry-delay:10000}")
    )
    public void handleUserStatusChanged(UserStatusChangedEvent event) {
        auditLogService.logEvent(event.getUser().getId(),
                "ASYNC_PROCESS_STARTED",
                "Processing status change notification task."
        );

        passService.sendStatusChangeNotification(event.getUser(), event.getReason());
    }

    /**
     * Fallback method executed when all retry attempts for sending the notification have failed.
     * <p>
     * Instead of throwing an exception to the caller (which is async anyway), it logs a
     * "NOTIFICATION_FAILED" event to the audit log. This allows administrators to monitor
     * delivery failures and potentially resend notifications manually.
     * </p>
     *
     * @param e     the final exception that caused the retry exhaustion.
     * @param event the original event that was being processed.
     */
    @Recover
    @SuppressWarnings("unused")
    public void handleRecovery(Exception e, UserStatusChangedEvent event) {
        auditLogService.logEvent(event.getUser().getId(),
                "NOTIFICATION_FAILED",
                "Failed to send email after retries. Final error: " + e.getMessage()
        );
    }
}