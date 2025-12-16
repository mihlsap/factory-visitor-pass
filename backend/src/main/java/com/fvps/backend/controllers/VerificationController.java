package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.verification.VerificationResponse;
import com.fvps.backend.services.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${app.endpoints.verify}")
@RequiredArgsConstructor
@Tag(name = "Verification", description = "Endpoints for security personnel to verify user access via QR code scanning.")
@SecurityRequirement(name = "bearerAuth")
public class VerificationController {

    private final VerificationService verificationService;

    @Operation(summary = "Verify User Access", description = "Checks if a user (identified by UUID from QR code) has the required security clearance level.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification performed successfully. Check 'accessGranted' field in response."),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<VerificationResponse> verifyUser(
            @Parameter(description = "User UUID (scanned from QR code)", required = true)
            @PathVariable UUID userId,

            @Parameter(description = "Minimum security clearance level required for this specific checkpoint", example = "1")
            @RequestParam(defaultValue = "1") int requiredLevel
    ) {
        return ResponseEntity.ok(verificationService.verifyUserAccess(userId, requiredLevel));
    }
}