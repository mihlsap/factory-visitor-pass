package com.fvps.backend.domain.dto.auth;

import com.fvps.backend.domain.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    @Schema(
            description = "JWT Token (Bearer). Null if 2FA is required (mfaEnabled=true).",
            example = "eyJhbGciOiJIUzI1NiJ9..."
    )
    private String token;

    @Schema(description = "Assigned user role", example = "USER")
    private UserRole role;

    @Schema(
            description = "If true, client must redirect user to 2FA verification screen. Token is not returned in this case.",
            example = "false"
    )
    private boolean mfaEnabled;
}