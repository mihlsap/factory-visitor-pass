package com.fvps.backend.domain.dto.auth;

import com.fvps.backend.domain.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private UserRole role; // Przyda się na frontendzie do ukrywania przycisków Admina
}