package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.auth.ChangePasswordRequest;
import com.fvps.backend.domain.dto.user.UpdateUserRequest;
import com.fvps.backend.domain.dto.user.UserPassDto;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.services.UserService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Operations related to the currently logged-in user (profile, password, pass generation).")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get My Profile", description = "Retrieves the profile details of the currently logged-in user.")
    @GetMapping("/me")
    public ResponseEntity<UserSummaryDto> getMyProfile(
            @Parameter(hidden = true) Authentication authentication
    ) {
        return ResponseEntity.ok(userService.getUserSummaryByEmail(authentication.getName()));
    }

    @Operation(summary = "Update Profile", description = "Updates user details (name, phone, company) and optionally uploads a new profile photo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Optimistic locking failure (version mismatch)")
    })
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> updateProfile(
            @Parameter(hidden = true) Authentication authentication,

            @Parameter(description = "Updated user data (JSON)", required = true, schema = @Schema(implementation = UpdateUserRequest.class))
            @Valid @RequestPart("data") UpdateUserRequest request,

            @Parameter(description = "New profile photo (optional)")
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        userService.updateUserData(authentication.getName(), request, photo);
        return ResponseEntity.ok(AppMessage.PROFILE_UPDATED.name());
    }

    @Operation(summary = "Change Password", description = "Changes the user's password. Requires current password for verification.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid current password or new password requirements not met")
    })
    @PatchMapping("/me/password")
    public ResponseEntity<String> changePassword(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(AppMessage.PASSWORD_CHANGED.name());
    }

    @Operation(summary = "Download Pass (PDF)", description = "Generates and downloads a Visitor Pass PDF if the user meets all requirements.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF file returned", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "User not eligible (e.g., trainings incomplete)")
    })
    @GetMapping("/me/download-pass")
    public ResponseEntity<byte[]> downloadMyPass(
            @Parameter(hidden = true) Authentication authentication
    ) {
        byte[] pdfBytes = userService.generateMyPassPdf(authentication.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=My_FVPS_Pass.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @Operation(summary = "Show Pass Data", description = "Retrieves data for displaying the digital pass (QR code, status, clearance level) in the frontend.")
    @GetMapping("/me/show-pass")
    public ResponseEntity<UserPassDto> getMyPassData(
            @Parameter(hidden = true) Authentication authentication
    ) {
        return ResponseEntity.ok(userService.getUserPassData(authentication.getName()));
    }
}