package com.fvps.backend.services;

import com.fvps.backend.domain.dto.auth.ChangePasswordRequest;
import com.fvps.backend.domain.dto.user.UpdateUserRequest;
import com.fvps.backend.domain.dto.user.UserPassDto;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Core service for managing user account lifecycles and profile data.
 * <p>
 * This interface handles both administrative tasks (blocking users, listing accounts)
 * and self-service operations (profile updates, password changes, pass generation).
 * </p>
 */
public interface UserService {

    /**
     * Retrieves the full entity of a user.
     *
     * @param id the UUID of the user.
     * @return the user entity.
     * @throws IllegalArgumentException if the user does not exist.
     */
    User getById(UUID id);

    /**
     * Retrieves a paginated list of users with summary details.
     * <p>
     * Optimized for administrative dashboards.
     * </p>
     *
     * @param pageable pagination information.
     * @return a page of user summaries.
     */
    Page<UserSummaryDto> getAllUsersSummary(Pageable pageable);

    /**
     * Updates the administrative status of a user (e.g. BLOCK, ACTIVATE, DELETE).
     * <p>
     * Changing status may trigger side effects like sending email notifications
     * or resetting security counters (e.g. failed login attempts).
     * </p>
     *
     * @param userId    the UUID of the user.
     * @param newStatus the new status to apply.
     */
    void changeUserStatus(UUID userId, UserStatus newStatus);

    /**
     * Retrieves user summary details by email.
     *
     * @param email the email address to search for.
     * @return the user summary DTO.
     */
    UserSummaryDto getUserSummaryByEmail(String email);

    /**
     * Retrieves user summary details by ID.
     *
     * @param id the UUID of the user.
     * @return the user summary DTO.
     */
    UserSummaryDto getUserSummaryById(UUID id);

    /**
     * Updates the user's personal data and profile photo.
     * <p>
     * <b>Concurrency Control:</b> This operation uses optimistic locking.
     * The request must contain the current {@code version} of the user entity.
     * If the version in the database has changed since the data was loaded,
     * the update will be rejected to prevent overwriting concurrent changes.
     * </p>
     *
     * @param email   the email of the user performing the update.
     * @param request the DTO containing updated fields (name, phone, company).
     * @param photo   (optional) a new profile picture file.
     */
    void updateUserData(String email, UpdateUserRequest request, MultipartFile photo);

    /**
     * Changes the user's password.
     * <p>
     * Requires the current password for verification.
     * </p>
     *
     * @param email   the email of the user.
     * @param request DTO containing old and new passwords.
     */
    void changePassword(String email, ChangePasswordRequest request);

    /**
     * Increments the failed login attempt counter for a user.
     * <p>
     * If the counter exceeds the configured threshold, the account is temporarily locked.
     * </p>
     *
     * @param userId the UUID of the user who failed to authenticate.
     */
    void registerFailedLogin(UUID userId);

    /**
     * Resets failed login counters and clears any lockout timestamps.
     * <p>
     * Typically called after a successful login or manual admin intervention.
     * </p>
     *
     * @param userId the UUID of the user.
     */
    void resetLockoutStats(UUID userId);

    /**
     * Generates a PDF visitor pass for the authenticated user ("My Pass").
     * <p>
     * Performs a just-in-time validation of training status and clearance level
     * to ensure the generated document is up to date.
     * </p>
     *
     * @param userEmail the email of the requesting user.
     * @return a byte array containing the PDF.
     * @throws IllegalStateException if the user is not active or lacks required trainings.
     */
    byte[] generateMyPassPdf(String userEmail);

    /**
     * Retrieves the data necessary to render the digital pass on the frontend/mobile view.
     *
     * @param email the email of the user.
     * @return DTO containing pass details (QR code, clearance level, validity).
     */
    UserPassDto getUserPassData(String email);
}