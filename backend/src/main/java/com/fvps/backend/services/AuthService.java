package com.fvps.backend.services;

import com.fvps.backend.domain.dto.auth.AuthResponse;
import com.fvps.backend.domain.dto.auth.LoginRequest;
import com.fvps.backend.domain.dto.auth.RegisterRequest;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    AuthResponse register(RegisterRequest request, MultipartFile photo);
    AuthResponse login(LoginRequest request);
}