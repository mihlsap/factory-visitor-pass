package com.fvps.backend.services.impl;

import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final AuditLogService auditLogService;

    @Value("${app.mail.address-from}")
    private String emailAddressFrom;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Asynchronous Execution:</b> Annotated with {@link Async}. The email sending process happens
     * in a separate thread to avoid blocking the user's HTTP request (reduces UI latency).</li>
     * <li><b>Audit Logging:</b> Automatically records an "EMAIL_SENT" event upon success or
     * "EMAIL_SENDING_FAILED" if an exception occurs during the SMTP handshake.</li>
     * <li><b>Error Handling:</b> Since this runs asynchronously, thrown exceptions are not propagated
     * to the caller immediately but are handled by the audit logger.</li>
     * </ul>
     * </p>
     */
    @Override
    @Async
    public void sendEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailAddressFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            auditLogService.logEvent("EMAIL_SENT", "Email sent to " + to);
        } catch (Exception e) {
            auditLogService.logEvent("EMAIL_SENDING_FAILED", "Email sending failed to " + to);
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }
}