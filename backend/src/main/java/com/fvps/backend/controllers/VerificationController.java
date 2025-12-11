package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.verification.VerificationResponse;
import com.fvps.backend.services.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("${app.endpoints.verify}")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @GetMapping("/{userId}")
    public ResponseEntity<VerificationResponse> verifyUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(verificationService.verifyUserAccess(userId));
    }
}