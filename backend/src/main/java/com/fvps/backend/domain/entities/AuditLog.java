package com.fvps.backend.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single audit log entry in the system.
 * <p>
 * This entity maps to the {@code audit_logs} table and stores detailed information about
 * security events, critical data changes, and user activities.
 * It serves as an immutable record for monitoring, debugging, and security compliance.
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    /**
     * Unique identifier for the log entry (Primary Key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The UUID of the user *affected* by this action (optional).
     * <p>
     * For example, if an Admin blocks a user, this field will contain the UUID of the blocked user.
     * It allows filtering logs specifically related to a given user account.
     * </p>
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * The identifier (usually email) of the user or system *performing* the action.
     * <p>
     * E.g., "admin@fvps.com" or "system-scheduler".
     * </p>
     */
    @Column(name = "actor")
    private String actor;

    /**
     * A short code or name describing the type of event.
     * <p>
     * Examples: "LOGIN_SUCCESS", "USER_BLOCK", "TRAINING_CREATED".
     * </p>
     */
    @Column(nullable = false)
    private String action;

    /**
     * Detailed description or payload associated with the event.
     * <p>
     * Could contain textual descriptions or JSON-like data (e.g., "Changed status from ACTIVE to BLOCKED").
     * </p>
     */
    @Column(nullable = false)
    private String details;

    /**
     * The exact date and time when the event occurred.
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * The IP address of the client that initiated the request (if applicable).
     */
    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * Checks equality based on the entity identifier (ID).
     * <p>
     * This implementation handles Hibernate proxies correctly by unwrapping the effective class.
     * Two audit logs are considered equal if they refer to the same persistence identity (same ID).
     * </p>
     *
     * @param o the object to compare with.
     * @return true if objects are the same entity instance or have the same ID.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AuditLog auditLog = (AuditLog) o;
        return getId() != null && Objects.equals(getId(), auditLog.getId());
    }

    /**
     * Returns the hash code based on the effective class.
     * <p>
     * Consistent with the {@code equals} implementation for JPA entities to ensure consistent behavior
     * across Hibernate states (transient, managed, detached).
     * </p>
     *
     * @return the hash code of the class.
     */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}