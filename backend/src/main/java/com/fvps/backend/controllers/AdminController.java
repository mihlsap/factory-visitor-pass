package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.entities.AuditLog;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.AdminService;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.TrainingService;
import com.fvps.backend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    private final AdminService adminService;
    private final TrainingService trainingService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @PostMapping("/trainings")
    public ResponseEntity<TrainingResponseDto> createTraining(@Valid @RequestBody CreateTrainingRequest request) {
        return ResponseEntity.ok(trainingService.createTraining(request));
    }

    @GetMapping("/trainings/{id}")
    public ResponseEntity<TrainingResponseDto> getTraining(@PathVariable UUID id) {
        return ResponseEntity.ok(trainingService.getTrainingDetails(id));
    }

    @PutMapping("/trainings/{id}")
    public ResponseEntity<TrainingResponseDto> updateTraining(@PathVariable UUID id, @RequestBody CreateTrainingRequest request) {
        return ResponseEntity.ok(trainingService.updateTraining(id, request));
    }

    @PostMapping("/trainings/{trainingId}/modules")
    public ResponseEntity<TrainingResponseDto> addModule(
            @PathVariable UUID trainingId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return ResponseEntity.ok(trainingService.addModuleToTraining(trainingId, request));
    }

    @PostMapping("/modules/{moduleId}/questions")
    public ResponseEntity<TrainingResponseDto> addQuestion(
            @PathVariable UUID moduleId,
            @Valid @RequestBody CreateQuestionRequest request
    ) {
        return ResponseEntity.ok(trainingService.addQuestionToModule(moduleId, request));
    }

    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<TrainingResponseDto> updateModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateModuleRequest request
    ) {
        return ResponseEntity.ok(trainingService.updateModule(moduleId, request));
    }

    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<TrainingResponseDto> deleteModule(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(trainingService.deleteModule(moduleId));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<TrainingResponseDto> updateQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request
    ) {
        return ResponseEntity.ok(trainingService.updateQuestion(questionId, request));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<TrainingResponseDto> deleteQuestion(@PathVariable UUID questionId) {
        return ResponseEntity.ok(trainingService.deleteQuestion(questionId));
    }

    @GetMapping("/trainings")
    public ResponseEntity<List<TrainingSummaryDto>> getAllTrainings() {
        return ResponseEntity.ok(trainingService.getAllTrainings());
    }

    @DeleteMapping("/trainings/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable UUID id) {
        trainingService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryDto>> getAllUsers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("surname"));
        return ResponseEntity.ok(userService.getAllUsersSummary(pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserSummaryDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserSummaryById(id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<String> assignTraining(@PathVariable UUID userId, @PathVariable UUID trainingId) {
        trainingService.assignTrainingToUser(userId, trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_ASSIGNED.name());
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<String> changeUserStatus(@PathVariable UUID userId, @RequestParam UserStatus status) {
        adminService.changeUserStatus(userId, status);
        return ResponseEntity.ok(AppMessage.USER_STATUS_CHANGED.name());
    }


    @GetMapping("/users/{userId}/pass-preview")
    public ResponseEntity<byte[]> generatePass(@PathVariable UUID userId) {
        try {
            byte[] pdfBytes = adminService.generatePassPdf(userId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=pass.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(null);
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(auditLogService.getAllLogs(pageable));
    }

    @GetMapping("/users/{userId}/trainings")
    public ResponseEntity<List<UserTrainingDto>> getUserTrainings(@PathVariable UUID userId) {
        return ResponseEntity.ok(trainingService.getUserTrainingsByUserId(userId));
    }

    @PostMapping("/users/{userId}/resend-pass")
    public ResponseEntity<String> resendPass(@PathVariable UUID userId) {
        adminService.resendPassEmail(userId);
        return ResponseEntity.ok(AppMessage.PASS_SENT.name());
    }

    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ModuleDto> getModule(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(trainingService.getModule(moduleId));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDto> getQuestion(@PathVariable UUID questionId) {
        return ResponseEntity.ok(trainingService.getQuestion(questionId));
    }
}