package com.fvps.backend.services;

import com.fvps.backend.domain.dto.auth.AuthResponse;
import com.fvps.backend.domain.dto.auth.LoginRequest;
import com.fvps.backend.domain.dto.auth.RegisterRequest;
import com.fvps.backend.domain.dto.auth.TwoFactorRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * Core service for user authentication, registration, and account security management.
 * <p>
 * This interface defines the contract for the entire authentication lifecycle,
 * including sign-up, Multi-Factor Authentication (MFA/2FA) flows, and password recovery.
 * </p>
 */
public interface AuthService {

    /**
     * Registers a new user in the system.
     * <p>
     * Creates a user account, processes the profile photo, and assigns an initial role
     * based on system policies.
     * </p>
     *
     * @param request the registration form data (name, email, password, etc.).
     * @param photo   the user's profile picture file.
     * @return an {@link AuthResponse} containing the generated JWT token and assigned role.
     * @throws IllegalArgumentException if the email is already taken.
     */
    AuthResponse register(RegisterRequest request, MultipartFile photo);

    /**
     * Initiates the login process.
     * <p>
     * <b>Note:</b> In this system, providing correct credentials does NOT immediately grant a JWT token.
     * Instead, it triggers the Two-Factor Authentication (2FA) flow by sending a verification code
     * to the user's email.
     * </p>
     *
     * @param request the credentials (email and password).
     * @return an {@link AuthResponse} indicating that MFA is enabled (token is null at this stage).
     * @throws RuntimeException if the account is blocked, locked, or credentials are invalid.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Initiates the password recovery process.
     * <p>
     * Sends a secure reset link to the user's email if the account exists.
     * </p>
     *
     * @param email the email address provided by the user.
     */
    void forgotPassword(String email);

    /**
     * Completes the password reset process using a verified token.
     *
     * @param email       the user's email address.
     * @param rawToken    the raw token string extracted from the reset link.
     * @param newPassword the new password to set.
     * @throws RuntimeException if the token is invalid, expired, or does not match the user.
     */
    void resetPassword(String email, String rawToken, String newPassword);

    /**
     * Verifies the 2FA code and issues the final access token.
     * <p>
     * This is the second step of the login process.
     * </p>
     *
     * @param request containing the email and the 6-digit code.
     * @return an {@link AuthResponse} containing the valid JWT access token.
     * @throws RuntimeException if the code is invalid or expired.
     */
    AuthResponse verifyTwoFactor(TwoFactorRequest request);

    /**
     * Terminates the current session.
     * <p>
     * Clears the security context and logs the logout event for audit purposes.
     * Since JWTs are stateless, this is primarily a client-side action, but the server
     * records the intent.
     * </p>
     */
    void logout();
}