package com.fvps.backend.events;

import com.fvps.backend.domain.entities.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Custom Spring Application Event triggered whenever a {@link User}'s status is modified.
 * <p>
 * This event allows different parts of the system (e.g. EmailService, AuditLogService) to react
 * to status changes (like blocking a user or unlocking an account) without being tightly coupled
 * to the service performing the update.
 * </p>
 */
@Getter
public class UserStatusChangedEvent extends ApplicationEvent {

    /**
     * The user entity whose status has been changed.
     */
    private final User user;

    /**
     * A text description explaining the cause of the status change.
     * <p>
     * Example: "Administrative block", "Too many failed login attempts", "Account activated via email".
     * Used primarily for audit logging purposes.
     * </p>
     */
    private final String reason;

    /**
     * Constructs a new UserStatusChangedEvent.
     *
     * @param source the object on which the event initially occurred (usually the service instance).
     * @param user   the user affected by the change.
     * @param reason the justification for the status change.
     */
    public UserStatusChangedEvent(Object source, User user, String reason) {
        super(source);
        this.user = user;
        this.reason = reason;
    }
}