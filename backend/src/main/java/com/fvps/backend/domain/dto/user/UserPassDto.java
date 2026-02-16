package com.fvps.backend.domain.dto.user;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
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

    @Schema(description = "URL to the user's profile photo", example = "/uploads/photos/user-123.jpg")
    private String photoUrl;

    @Schema(description = "User's email address", example = "john.doe@fvps.com")
    private String email;

    @Schema(description = "User's phone number", example = "+48123456789")
    private String phoneNumber;

    @Schema(description = "Security clearance level calculated from valid trainings", example = "2")
    private int clearanceLevel;

    @Schema(
            description = "String content of the QR code (usually encrypted data)",
            example = "U2FsdGVkX1+..."
    )
    private String qrCodeContent;

    @Schema(description = "List of valid trainings completed by the user")
    private List<UserTrainingDto> validTrainings;
}