package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.auth.AuthResponse;
import com.fvps.backend.domain.dto.auth.LoginRequest;
import com.fvps.backend.domain.dto.auth.RegisterRequest;
import com.fvps.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<AuthResponse> register(
            // @RequestPart mapuje część formularza na obiekt JSON, a część na plik
            @RequestPart("data") RegisterRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        return ResponseEntity.ok(authService.register(request, photo));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}