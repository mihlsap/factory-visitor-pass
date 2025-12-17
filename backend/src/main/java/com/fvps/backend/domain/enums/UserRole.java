package com.fvps.backend.domain.enums;

/**
 * Defines the security roles available within the application.
 * <p>
 * These roles determine the authorisation levels and access rights to specific API endpoints
 * and system features. They are used by Spring Security to control access decisions.
 * </p>
 */
public enum UserRole {

    /**
     * Administrator with full system access.
     * <p>
     * Can manage users, trainings, system configuration, and view audit logs.
     * </p>
     */
    ADMIN,

    /**
     * Standard internal employee.
     * <p>
     * Has access to their own profile, assigned trainings, and personal pass generation.
     * Represents a permanent worker of the factory/company.
     * </p>
     */
    EMPLOYEE,

    /**
     * Security personnel (Gate Guard).
     * <p>
     * Has restricted access, primarily focused on verifying user passes via QR code scanning
     * at entry checkpoints.
     * </p>
     */
    GUARD,

    /**
     * External visitor or temporary contractor.
     * <p>
     * Has access to their own profile, assigned trainings, and personal pass generation.
     * </p>
     */
    GUEST
}