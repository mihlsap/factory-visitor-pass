package com.fvps.backend.services;

import com.fvps.backend.domain.dto.audit.AuditLogDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service responsible for recording system events for security and compliance
 * purposes.
 * <p>
 * This service acts as a centralised mechanism to track "who did what, when,
 * and from where".
 * It captures critical actions such as login attempts, data modifications, and
 * security alerts.
 * </p>
 */
public interface AuditLogService {

    /**
     * Logs an event related to a specific target user.
     * <p>
     * Use this method when an action directly affects a user account (e.g. "User
     * blocked", "Pass generated").
     * The system automatically resolves the "Actor" (who performed the action) and
     * the request metadata (IP address).
     * </p>
     *
     * @param userId  the UUID of the user affected by the action (the target).
     * @param action  a short, uppercase code identifying the event type (e.g.
     *                "USER_BLOCKED").
     * @param details a human-readable description or additional context (e.g.
     *                "Reason: Policy violation").
     */
    void logEvent(UUID userId, String action, String details);

    /**
     * Logs a general system event not tied to a specific target user.
     * <p>
     * Use this method for system-wide events (e.g. "Scheduler started", "Database
     * connection error")
     * or anonymous actions (e.g. "Failed login attempt" where the user ID is
     * unknown).
     * </p>
     *
     * @param action  a short, uppercase code identifying the event type.
     * @param details a human-readable description of the event.
     */
    void logEvent(String action, String details);

    /**
     * Retrieves audit logs based on dynamic filtering criteria.
     * <p>
     * Allows administrators to perform precise searches, e.g., "Show me all logins
     * by user X yesterday".
     * Any parameter can be null to ignore that filter.
     * </p>
     *
     * @param actor     partial match of the actor's email/name.
     * @param action    exact match of the action code.
     * @param startDate filter logs after this timestamp.
     * @param endDate   filter logs before this timestamp.
     * @param search    global search term matching actor, action, details, or IP.
     * @param pageable  pagination info.
     * @return a filtered page of audit logs.
     */
    Page<AuditLogDto> getLogsWithFilters(
            String actor,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String search,
            Pageable pageable);
}