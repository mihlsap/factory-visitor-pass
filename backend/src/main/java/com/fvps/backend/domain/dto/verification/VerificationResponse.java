package com.fvps.backend.domain.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {
    private boolean accessGranted;
    private String message;
    private String fullName;
    private String photoUrl;
}