package com.fvps.backend.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @Schema(
            description = "Adres email użytkownika",
            example = "jan.kowalski@fvps.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String email;

    @Schema(
            description = "Hasło użytkownika",
            example = "StrongPass1!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String password;
}