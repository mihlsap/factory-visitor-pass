package com.fvps.backend.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @Schema(description = "First name", example = "Jan", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "First name is required")
    private String name;

    @Schema(description = "Last name", example = "Kowalski", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Last name is required")
    private String surname;

    @Schema(description = "Email address (must be unique)", example = "jan.kowalski@fvps.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(
            description = "Password (min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char)",
            example = "StrongPass1!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
            message = "Password must be at least 8 characters long, contain uppercase and lowercase letter, digit and special character."
    )
    private String password;

    @Schema(
            description = "Company name. Automatically overridden for internal employees (@fvps.com domain).",
            example = "External Logistics Ltd."
    )
    private String companyName;

    @Schema(
            description = "Phone number in E.164 format",
            example = "+48123456789"
    )
    @Pattern(
            regexp = "^\\+[0-9]{6,15}$",
            message = "Invalid phone number format. Use international E.164 format (e.g., +48123456789)"
    )
    private String phoneNumber;
}