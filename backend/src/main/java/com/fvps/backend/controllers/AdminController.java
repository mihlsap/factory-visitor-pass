package com.fvps.backend.controllers;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trainings")
@RequiredArgsConstructor
public class AdminController {

    private final TrainingService trainingService;

    @PostMapping
    public ResponseEntity<Training> createTraining(@RequestBody Training training) {
        return ResponseEntity.ok(trainingService.createTraining(training));
    }

    @GetMapping
    public ResponseEntity<List<Training>> getAllTrainings() {
        return ResponseEntity.ok(trainingService.getAllTrainings());
    }
}