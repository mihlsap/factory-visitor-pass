package com.fvps.backend.domain.enums;

/**
 * Registry of standardised application response codes.
 * <p>
 * These constants serve as stable identifiers for business events and outcomes.
 * The frontend application uses these codes to resolve localised, user-friendly
 * messages (i18n).
 * For example, {@code ACCESS_DENIED_BLOCKED} might be translated to "Konto
 * zablokowane" (PL)
 * or "Account Blocked" (EN).
 * </p>
 */
public enum AppMessage {

    // --- Verification & Access Control ---

    /**
     * User meets all security requirements (status active, training valid,
     * clearance sufficient).
     */
    ACCESS_GRANTED,

    /**
     * Access denied because the user's account status is {@code BLOCKED} or
     * {@code DELETED}.
     */
    ACCESS_DENIED_BLOCKED,

    /**
     * Access denied because the user has not completed the required training for
     * this zone.
     */
    ACCESS_DENIED_NO_TRAINING,

    /**
     * Access denied because the QR code corresponds to a user UUID that does not
     * exist in the database.
     */
    ACCESS_DENIED_USER_NOT_FOUND,

    /**
     * Access denied because the user's clearance level is lower than the zone's
     * requirement.
     */
    ACCESS_DENIED_LOW_LEVEL,

    // --- Authentication ---

    /**
     * Login process completed successfully (2FA verified, token issued).
     */
    LOGIN_SUCCESS,

    /**
     * New user account created successfully.
     */
    REGISTER_SUCCESS,

    /**
     * Password recovery email has been dispatched.
     */
    PASSWORD_RESET_LINK_SENT,

    /**
     * Password has been successfully reset using a valid token.
     */
    PASSWORD_RESET_COMPLETE,

    /**
     * User authenticated and changed their password manually.
     */
    PASSWORD_CHANGE_SUCCESS,

    /**
     * User session terminated.
     */
    LOGOUT_SUCCESS,

    // --- User Profile ---

    /**
     * User profile details (name, photo, phone) have been updated.
     */
    PROFILE_UPDATED,

    /**
     * Redundant alias for PASSWORD_CHANGE_SUCCESS (consider deprecating one).
     */
    PASSWORD_CHANGED,

    // --- Training Progress ---

    /**
     * A standard content module (Video/PDF) has been marked as watched/read.
     */
    MODULE_COMPLETED,

    /**
     * User achieved a score equal to or higher than the passing threshold.
     */
    QUIZ_PASSED,

    /**
     * User failed to achieve the required score.
     */
    QUIZ_FAILED,

    // --- Administrative Actions ---

    /**
     * A new training definition has been added to the system.
     */
    TRAINING_CREATED,

    /**
     * A training definition has been permanently removed.
     */
    TRAINING_DELETED,

    /**
     * Existing training details or structure were modified.
     */
    TRAINING_UPDATED,

    /**
     * An administrator manually assigned a training to a user.
     */
    TRAINING_ASSIGNED,

    /**
     * Bulk assignment of trainings based on security level completed successfully.
     */
    TRAININGS_ASSIGNED_BULK,

    /**
     * User status changed (e.g. from ACTIVE to BLOCKED).
     */
    USER_STATUS_CHANGED,

    /**
     * User account was deleted (soft or hard delete).
     */
    USER_DELETED,

    /**
     * User role has been modified by an administrator.
     */
    USER_ROLE_CHANGED,

    /**
     * Visitor pass PDF was generated and sent/downloaded.
     */
    PASS_SENT,

    /**
     * Admin previewed the pass PDF without changing the system state.
     */
    PASS_PREVIEW_GENERATED,

    /**
     * A user's training completion was manually revoked by an admin.
     */
    TRAINING_REVOKED,

    // --- Extended Audit Events (Consistency Refactor) ---

    // Authentication & Access
    LOGIN_FAILED,
    LOGIN_BLOCKED,
    LOGIN_LOCKED,
    ACCOUNT_LOCKED,
    LOGIN_2FA_INIT,
    LOGIN_2FA_FAILED,
    LOGIN_FAILED_UNKNOWN,
    LOCKOUT_EXPIRED,

    // Password Management
    PASSWORD_CHANGE_FAILED,
    PASSWORD_RESET_FAILED,
    PASSWORD_RESET_ATTEMPT_UNKNOWN,
    PASSWORD_RESET_INIT,

    // Pass Handling
    PASS_DOWNLOADED,
    PASS_AUTO_SEND_FAILED,
    COMPLETION_NOTIFICATION_SENT,

    // Content Management (Granular)
    MODULE_ADDED,
    MODULE_UPDATED,
    MODULE_DELETED,
    QUESTION_ADDED,
    QUESTION_UPDATED,
    QUESTION_DELETED,

    // User & Training Management
    CLEARANCE_CHANGED, // Replaces CLEARANCE_UPDATED for consistency
    TRAINING_UNASSIGNED,
    TRAINING_PROGRESS_RESET,
    BULK_ASSIGNMENT_SKIPPED,
    TRAINING_VALIDITY_RECALCULATED,

    // System / Notifications
    EMAIL_SENT,
    EMAIL_SENDING_FAILED,
    FILE_CLEANUP_WARNING,
    FILE_DELETE_ERROR,

    // Legacy / Aliases (to be standardized)
    USER_STATUS_CHANGE
}