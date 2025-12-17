package com.fvps.backend.domain.enums;

/**
 * Represents the lifecycle status of a user account.
 * <p>
 * This status determines whether a user can log in and how the system treats their account
 * (e.g. visibility in lists, pass validity).
 * </p>
 */
public enum UserStatus {

    /**
     * The account is fully active and functional.
     * <p>
     * The user can log in, complete trainings, and generate visitor passes.
     * </p>
     */
    ACTIVE,

    /**
     * The account has been administratively blocked.
     * <p>
     * Login is disabled. This status is typically used for disciplinary actions
     * or when an employee is temporarily suspended.
     * </p>
     */
    BLOCKED,

    /**
     * The account has been marked as deleted (Soft Delete).
     * <p>
     * The user data is retained in the database for audit integrity and historical records,
     * but the account is effectively removed from active system operations.
     * Authentication is permanently disabled for this status.
     * </p>
     */
    DELETED
}