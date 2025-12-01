package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.TrainingService;
import com.fvps.backend.services.UserService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/verify") // To pasuje do linku w QR: http://localhost:8080/verify/{id}
@RequiredArgsConstructor
public class VerificationController {

    private final UserService userService;
    private final TrainingService trainingService;

    @GetMapping("/{userId}")
    public ResponseEntity<VerificationResponse> verifyUser(@PathVariable UUID userId) {
        // 1. Pobierz usera
        User user;
        try {
            user = userService.getById(userId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new VerificationResponse(false, "Nieznany użytkownik", null, null));
        }

        // 2. Sprawdź status konta (BLOCKED/DELETED)
        if (user.getStatus() != UserStatus.ACTIVE) {
            return ResponseEntity.ok(new VerificationResponse(
                    false,
                    "ZAKAZ WSTĘPU: Konto zablokowane lub usunięte.",
                    user.getName() + " " + user.getSurname(),
                    user.getPhotoUrl()
            ));
        }

        // 3. Sprawdź czy ma ważne szkolenia
        List<UserTrainingDto> validTrainings = trainingService.getValidTrainingsForUser(userId);

        if (validTrainings.isEmpty()) {
            return ResponseEntity.ok(new VerificationResponse(
                    false,
                    "BRAK WAŻNYCH UPRAWNIEŃ (Szkolenia wygasły lub nieukończone).",
                    user.getName() + " " + user.getSurname(),
                    user.getPhotoUrl()
            ));
        }

        // 4. Sukces
        return ResponseEntity.ok(new VerificationResponse(
                true,
                "WSTĘP DOZWOLONY. Ważnych szkoleń: " + validTrainings.size(),
                user.getName() + " " + user.getSurname(),
                user.getPhotoUrl()
        ));
    }

    // Proste DTO wewnętrzne
    @Data
    @Builder
    static class VerificationResponse {
        private boolean accessGranted;
        private String message;
        private String fullName;
        private String photoUrl;
    }
}