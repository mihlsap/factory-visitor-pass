package com.fvps.backend.domain.dto.user;

import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {

    @Schema(description = "User's unique identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Email address", example = "john.doe@fvps.com")
    private String email;

    @Schema(description = "First name", example = "John")
    private String name;

    @Schema(description = "Last name", example = "Doe")
    private String surname;

    @Schema(description = "Company name", example = "External Logistics Ltd.")
    private String companyName;

    @Schema(description = "Assigned system role", example = "USER")
    private UserRole role;

    @Schema(description = "Current account status", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "URL to the user's profile photo", example = "/uploads/photos/user-123.jpg")
    private String photoUrl;

    @Schema(description = "User's phone number", example = "+48123456789")
    private String phoneNumber;

    @Schema(description = "Optimistic locking version", example = "1")
    private Long version;

    @Schema(description = "Current security clearance level", example = "2")
    private int clearanceLevel;
}