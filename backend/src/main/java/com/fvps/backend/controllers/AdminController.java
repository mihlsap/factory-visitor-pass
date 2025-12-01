package com.fvps.backend.controllers;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.services.PdfGeneratorService;
import com.fvps.backend.services.TrainingService;
import com.fvps.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TrainingService trainingService;
    private final PdfGeneratorService pdfGeneratorService;
    private final UserService userService;

    // --- Sekcja Szkoleń ---

    @PostMapping("/trainings")
    public ResponseEntity<Training> createTraining(@RequestBody Training training) {
        return ResponseEntity.ok(trainingService.createTraining(training));
    }

    @GetMapping("/trainings")
    public ResponseEntity<List<Training>> getAllTrainings() {
        return ResponseEntity.ok(trainingService.getAllTrainings());
    }

    // --- Sekcja Użytkowników i Przypisywania ---

    @PostMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<String> assignTraining(
            @PathVariable UUID userId,
            @PathVariable UUID trainingId
    ) {
        trainingService.assignTrainingToUser(userId, trainingId);
        return ResponseEntity.ok("Szkolenie zostało pomyślnie przypisane do użytkownika.");
    }

    @GetMapping("/users/{userId}/pass-preview")
    public ResponseEntity<byte[]> generatePass(@PathVariable UUID userId) {
        // 1. Pobierz dane usera
        var user = userService.getById(userId);

        // 2. Pobierz jego ważne szkolenia
        var validTrainings = trainingService.getValidTrainingsForUser(userId);

        // 3. Generuj PDF
        byte[] pdfBytes = pdfGeneratorService.generatePassPdf(user, validTrainings);

        // 4. Zwróć plik
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=pass_" + user.getSurname() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}