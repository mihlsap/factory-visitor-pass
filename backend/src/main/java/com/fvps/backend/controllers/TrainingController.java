package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;

    // Pobierz moje szkolenia
    @GetMapping("/my")
    public ResponseEntity<List<UserTrainingDto>> getMyTrainings(Authentication authentication) {
        return ResponseEntity.ok(trainingService.getUserTrainings(authentication.getName()));
    }

    // Zakończ moduł (Wideo/PDF)
    @PostMapping("/{trainingId}/module/{moduleId}/complete")
    public ResponseEntity<String> completeModule(
            Authentication authentication,
            @PathVariable UUID trainingId,
            @PathVariable UUID moduleId
    ) {
        trainingService.completeModule(authentication.getName(), trainingId, moduleId);
        return ResponseEntity.ok("Moduł ukończony.");
    }

    // Wyślij odpowiedzi do Quizu
    @PostMapping("/{trainingId}/module/{moduleId}/submit-quiz")
    public ResponseEntity<String> submitQuiz(
            Authentication authentication,
            @PathVariable UUID trainingId,
            @PathVariable UUID moduleId,
            @RequestBody QuizSubmissionDto submission
    ) {
        boolean passed = trainingService.submitQuiz(authentication.getName(), trainingId, moduleId, submission);

        if (passed) {
            return ResponseEntity.ok("Gratulacje! Quiz zaliczony.");
        } else {
            // Zwracamy 200 OK (bo request się udał), ale z komunikatem o oblaniu.
            // Frontend powinien obsłużyć to i wyświetlić "Spróbuj ponownie".
            return ResponseEntity.ok("Niestety, nie zaliczyłeś quizu. Spróbuj ponownie.");
        }
    }
}