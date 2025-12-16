package com.fvps.backend.domain.dto.user;

import com.fvps.backend.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserPassDto {

    @Schema(description = "User's unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Full name (First name + Last name)", example = "John Doe")
    private String fullName;

    @Schema(description = "Company name", example = "External Logistics Ltd.")
    private String companyName;

    @Schema(description = "Current status. Determines if the pass is valid (must be ACTIVE).", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "List of titles of currently valid trainings", example = "[\"OHS Safety Level 1\", \"Fire Safety\"]")
    private List<String> validTrainings;

    @Schema(description = "Security clearance level calculated from valid trainings", example = "2")
    private int securityClearanceLevel;

    @Schema(
            description = "Base64 encoded QR code image (PNG format). Contains encrypted user ID and timestamp.",
            example = "iVBORw0KGgoAAAANSUhEUgAA..."
    )
    private String qrCodeBase64;
}