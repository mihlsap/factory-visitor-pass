package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a user of the system.
 * <p>
 * This entity maps to the {@code users} table and contains all information related to
 * authentication, authorization, and personal profile data. It handles critical security
 * features like account locking (brute-force protection), two-factor authentication (2FA),
 * and password recovery.
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    /**
     * Unique identifier for the user (Primary Key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User's email address, serving as the unique login identifier.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * The encrypted (hashed) password.
     * <p>
     * Never store plain-text passwords here. Use {@link org.springframework.security.crypto.password.PasswordEncoder}.
     * </p>
     */
    @Column(nullable = false)
    private String password;

    /**
     * User's first name.
     */
    @Column(nullable = false)
    private String name;

    /**
     * User's surname.
     */
    @Column(nullable = false)
    private String surname;

    /**
     * The role assigned to the user (e.g., USER, ADMIN, GUARD).
     * <p>
     * Determines access to specific API endpoints.
     * </p>
     */
    @Enumerated(EnumType.STRING)
    private UserRole role;

    /**
     * The current status of the account (e.g., ACTIVE, BLOCKED, LOCKED).
     */
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    /**
     * Filename of the uploaded profile photo.
     */
    private String photoUrl;

    /**
     * User's company name.
     */
    private String companyName;

    /**
     * User's phone number.
     */
    @Column(name = "phone_number")
    private String phoneNumber;

    /**
     * Counter for consecutive failed login attempts.
     * <p>
     * Reset to 0 after a successful login. If it exceeds the limit, the account is temporarily locked.
     * </p>
     */
    @Builder.Default
    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    /**
     * Timestamp indicating when the temporary lock expires.
     * <p>
     * Null if the account is not currently locked.
     * </p>
     */
    @Column(name = "lockout_time")
    private LocalDateTime lockoutTime;

    /**
     * Token sent via email for password recovery.
     */
    private String resetToken;

    /**
     * Expiration time for the password reset token.
     */
    private LocalDateTime resetTokenExpiry;

    // --- 2FA ---

    /**
     * The 6-digit code generated for Two-Factor Authentication.
     */
    private String twoFactorCode;

    /**
     * Expiration time for the 2FA code.
     */
    private LocalDateTime twoFactorCodeExpiry;

    /**
     * Date and time when the user account was created.
     */
    private LocalDateTime createdAt;

    /**
     * Date and time when the user account was last updated.
     */
    private LocalDateTime updatedAt;

    /**
     * Timestamp of the last successful login.
     */
    private LocalDateTime lastLogin;

    /**
     * Optimistic locking version.
     * <p>
     * Prevents lost updates when multiple threads/requests try to modify the user data simultaneously.
     * </p>
     */
    @Version
    private Long version;

    /**
     * The security clearance level derived from completed trainings.
     * <p>
     * Used by Security Guards to verify if the user can enter specific zones.
     * Default is 0 (no clearance).
     * </p>
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int clearanceLevel = 0;

    /**
     * Automatically sets creation and update timestamps before persisting.
     * Sets the default status to ACTIVE if not provided.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /**
     * Automatically updates the timestamp before updating the entity.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks equality based on the entity identifier (ID).
     * <p>
     * Handles Hibernate proxies correctly.
     * </p>
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        User user = (User) o;
        return getId() != null && Objects.equals(getId(), user.getId());
    }

    /**
     * Returns the hash code based on the effective class type.
     */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}