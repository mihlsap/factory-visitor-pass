package com.fvps.backend.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class TwoFactorRequest {

    @Schema(description = "Email address of the user verifying identity", example = "jan.kowalski@fvps.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "6-digit verification code sent via email", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}