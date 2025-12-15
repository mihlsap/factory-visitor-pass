package com.fvps.backend.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @Schema(description = "Current password (for verification)", example = "OldPass1!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @Schema(
            description = "New password (must meet security requirements)",
            example = "NewStrongPass2@",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "New password is required")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
            message = "Password must be at least 8 characters long, contain uppercase and lowercase letter, digit and special character."
    )
    private String newPassword;
}