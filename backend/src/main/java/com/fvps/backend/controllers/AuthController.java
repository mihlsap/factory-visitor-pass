package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.auth.*;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, 2FA, and password management.")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register User", description = "Registers a new user account. Requires a profile photo upload.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or email already exists")
    })
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> register(
            @Parameter(description = "Registration data (JSON)", required = true, schema = @Schema(implementation = RegisterRequest.class))
            @Valid @RequestPart("data") RegisterRequest request,

            @Parameter(description = "Profile photo file (JPEG/PNG)", required = true)
            @RequestPart(value = "photo") MultipartFile photo
    ) {
        return ResponseEntity.ok(authService.register(request, photo));
    }

    @Operation(summary = "Login", description = "Authenticates a user. Returns a JWT token or a 2FA requirement flag.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful or 2FA code sent"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account is locked or blocked")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Verify 2FA", description = "Verifies the 2FA code sent to the user's email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification successful, JWT token returned"),
            @ApiResponse(responseCode = "400", description = "Invalid code")
    })
    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactor(@RequestBody TwoFactorRequest request) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request));
    }

    @Operation(summary = "Forgot Password", description = "Initiates the password reset process by sending a reset link via email.")
    @ApiResponse(responseCode = "200", description = "Reset link sent (if email exists)")
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @Parameter(description = "User's email address", required = true)
            @RequestParam String email
    ) {
        authService.forgotPassword(email);
        return ResponseEntity.ok(AppMessage.PASSWORD_RESET_LINK_SENT.name());
    }

    @Operation(summary = "Reset Password", description = "Sets a new password using a valid reset token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid token or password requirements not met")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail(), request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(AppMessage.PASSWORD_RESET_COMPLETE.name());
    }

    @Operation(summary = "Logout", description = "Logs the user out (server-side audit) and invalidates the context.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        authService.logout();
        return ResponseEntity.ok(AppMessage.LOGOUT_SUCCESS.name());
    }
}