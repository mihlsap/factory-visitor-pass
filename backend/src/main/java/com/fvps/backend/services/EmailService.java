package com.fvps.backend.services;

/**
 * Service abstraction for sending email notifications.
 * <p>
 * Defines the contract for dispatching electronic mail, allowing the underlying implementation
 * (e.g. SMTP, API-based) to be swapped without affecting business logic.
 * </p>
 */
public interface EmailService {

    /**
     * Dispatches a simple text-based email to a specific recipient.
     *
     * @param to      the recipient's email address.
     * @param subject the subject line of the email.
     * @param content the plain text body of the message.
     */
    void sendEmail(String to, String subject, String content);
}