package com.fvps.backend.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Schema(description = "First name", example = "John", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "First name is required")
    private String name;

    @Schema(description = "Last name", example = "Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Last name is required")
    private String surname;

    @Schema(description = "Company name", example = "External Logistics Ltd.")
    private String companyName;

    @Schema(
            description = "Phone number in E.164 format",
            example = "+48123456789"
    )
    @Pattern(regexp = "^\\+[0-9]{6,15}$", message = "Invalid phone number format")
    private String phoneNumber;

    @Schema(
            description = "Optimistic locking version. Must match the current version of the user entity to prevent concurrent modification conflicts.",
            example = "1"
    )
    private Long version;
}