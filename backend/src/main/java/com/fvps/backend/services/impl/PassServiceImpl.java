package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.EmailService;
import com.fvps.backend.services.PassService;
import lombok.RequiredArgsConstructor;
import com.fvps.backend.domain.enums.AppMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PassServiceImpl implements PassService {

    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final MessageSource messageSource;
    private final Locale defaultLocale;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * This implementation sends a <b>lightweight notification</b> containing a link
     * to the frontend
     * ("{@code /my-pass}") rather than attaching the PDF file directly.
     * <br>
     * Reasons:
     * <ul>
     * <li><b>Security:</b> Prevents sensitive data (QR codes) from persisting in
     * email inboxes.</li>
     * <li><b>Validity:</b> Ensures the user always downloads the most up-to-date
     * version of the pass from the system.</li>
     * </ul>
     * </p>
     */
    @Override
    public void sendPassCompletionNotification(User user) {
        String subject = messageSource.getMessage("email.pass.completion.subject", null, defaultLocale);

        Object[] args = { user.getName(), frontendUrl + "/my-pass" };
        String body = messageSource.getMessage("email.pass.completion.body", args, defaultLocale);

        emailService.sendEmail(user.getEmail(), subject, body);

        auditLogService.logEvent(user.getId(), AppMessage.COMPLETION_NOTIFICATION_SENT.name(),
                "Sent completion notification (no PDF).");
    }

    @Override
    public void sendStatusChangeNotification(User user, String reason) {
        String subject = messageSource.getMessage("email.status.change.subject", null, defaultLocale);

        Object[] args = { user.getName(), reason, frontendUrl };
        String body = messageSource.getMessage("email.status.change.body", args, defaultLocale);

        emailService.sendEmail(user.getEmail(), subject, body);

        auditLogService.logEvent(
                user.getId(),
                AppMessage.EMAIL_SENT.name(),
                "Sent status change notification. Reason: " + reason);
    }
}