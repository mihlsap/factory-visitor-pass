package com.fvps.backend.domain.dto.verification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {

    @Schema(
            description = "Indicates whether the user is allowed to enter the facility.",
            example = "true"
    )
    private boolean accessGranted;

    @Schema(
            description = "Detailed message regarding the verification result (e.g., reason for denial).",
            example = "Access granted. Clearance Level 2 confirmed."
    )
    private String message;

    @Schema(
            description = "Full name of the verified user (for visual confirmation).",
            example = "John Doe"
    )
    private String fullName;

    @Schema(
            description = "URL to the user's profile photo (for visual confirmation by security guard).",
            example = "/uploads/photos/user-550e8400-e29b-41d4-a716-446655440000.jpg"
    )
    private String photoUrl;
}