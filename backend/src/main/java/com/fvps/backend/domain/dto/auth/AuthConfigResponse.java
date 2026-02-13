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
public class AuthConfigResponse {

    @Schema(description = "The official company email domain", example = "fvps.com")
    private String companyDomain;

    @Schema(description = "The official company name", example = "FVPS Systems Incorporated")
    private String companyName;
}