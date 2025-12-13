package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.entities.AuditLog;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TrainingContentService trainingContentService;
    private final TrainingProgressService trainingProgressService;

    @PostMapping("/trainings")
    public ResponseEntity<TrainingResponseDto> createTraining(@Valid @RequestBody CreateTrainingRequest request) {
        return ResponseEntity.ok(trainingContentService.createTraining(request));
    }

    @GetMapping("/trainings/{id}")
    public ResponseEntity<TrainingResponseDto> getTraining(@PathVariable UUID id) {
        return ResponseEntity.ok(trainingContentService.getTrainingDetails(id));
    }

    @PutMapping("/trainings/{id}")
    public ResponseEntity<TrainingResponseDto> updateTraining(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTrainingRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateTraining(id, request));
    }

    @PostMapping("/trainings/{trainingId}/modules")
    public ResponseEntity<TrainingResponseDto> addModule(
            @PathVariable UUID trainingId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.addModuleToTraining(trainingId, request));
    }

    @PostMapping("/modules/{moduleId}/questions")
    public ResponseEntity<TrainingResponseDto> addQuestion(
            @PathVariable UUID moduleId,
            @Valid @RequestBody CreateQuestionRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.addQuestionToModule(moduleId, request));
    }

    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<TrainingResponseDto> updateModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateModuleRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateModule(moduleId, request));
    }

    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<TrainingResponseDto> deleteModule(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(trainingContentService.deleteModule(moduleId));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<TrainingResponseDto> updateQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateQuestion(questionId, request));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<TrainingResponseDto> deleteQuestion(@PathVariable UUID questionId) {
        return ResponseEntity.ok(trainingContentService.deleteQuestion(questionId));
    }

    @GetMapping("/trainings")
    public ResponseEntity<Page<TrainingSummaryDto>> getAllTrainings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("title"));
        return ResponseEntity.ok(trainingContentService.getAllTrainings(pageable));
    }

    @DeleteMapping("/trainings/{id}")
    public ResponseEntity<Void> deleteTraining(@PathVariable UUID id) {
        trainingContentService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("surname"));
        return ResponseEntity.ok(userService.getAllUsersSummary(pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserSummaryDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserSummaryById(id));
    }

    @PostMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<String> assignTraining(
            @PathVariable UUID userId,
            @PathVariable UUID trainingId
    ) {
        trainingProgressService.assignTrainingToUser(userId, trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_ASSIGNED.name());
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<String> changeUserStatus(
            @PathVariable UUID userId,
            @RequestParam UserStatus status
    ) {
        adminService.changeUserStatus(userId, status);
        return ResponseEntity.ok(AppMessage.USER_STATUS_CHANGED.name());
    }


    @GetMapping("/users/{userId}/pass-preview")
    public ResponseEntity<byte[]> generatePass(@PathVariable UUID userId) {
        byte[] pdfBytes = adminService.generatePassPdf(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=pass.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
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
    public ResponseEntity<Page<UserTrainingDto>> getUserTrainings(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(trainingProgressService.getUserTrainingsByUserId(userId, pageable));
    }

    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ModuleDto> getModule(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(trainingContentService.getModule(moduleId));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDto> getQuestion(@PathVariable UUID questionId) {
        return ResponseEntity.ok(trainingContentService.getQuestion(questionId));
    }

    @DeleteMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<Void> unassignTraining(@PathVariable UUID userId, @PathVariable UUID trainingId) {
        trainingProgressService.unassignTrainingFromUser(userId, trainingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/trainings/{trainingId}/revoke-completion")
    public ResponseEntity<String> revokeCompletion(@PathVariable UUID userId, @PathVariable UUID trainingId) {
        trainingProgressService.revokeTrainingCompletion(userId, trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_REVOKED.name());
    }

    @PostMapping("/users/{userId}/assign-level/{level}")
    public ResponseEntity<String> assignTrainingsByLevel(
            @PathVariable UUID userId,
            @PathVariable int level
    ) {
        trainingProgressService.assignTrainingsByLevelToUser(userId, level);
        return ResponseEntity.ok("Successfully assigned all missing trainings for level " + level);
    }
}