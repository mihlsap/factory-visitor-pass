package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.entities.AuditLog;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Panel", description = "Management endpoints for Users, Trainings, Modules, Questions, and Logs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TrainingContentService trainingContentService;
    private final TrainingProgressService trainingProgressService;

    @Operation(summary = "Create Training", description = "Creates a new training definition.")
    @PostMapping("/trainings")
    public ResponseEntity<TrainingResponseDto> createTraining(@Valid @RequestBody CreateTrainingRequest request) {
        return ResponseEntity.ok(trainingContentService.createTraining(request));
    }

    @Operation(summary = "Get Training Details", description = "Retrieves full details of a specific training including modules and questions.")
    @GetMapping("/trainings/{id}")
    public ResponseEntity<TrainingResponseDto> getTraining(
            @Parameter(description = "Training UUID") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(trainingContentService.getTrainingDetails(id));
    }

    @Operation(summary = "Update Training", description = "Updates main training properties.")
    @PutMapping("/trainings/{id}")
    public ResponseEntity<TrainingResponseDto> updateTraining(
            @Parameter(description = "Training UUID") @PathVariable UUID id,
            @Valid @RequestBody CreateTrainingRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateTraining(id, request));
    }

    @Operation(summary = "Get All Trainings", description = "Retrieves a paginated list of trainings (summary view).")
    @GetMapping("/trainings")
    public ResponseEntity<Page<TrainingSummaryDto>> getAllTrainings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("title"));
        return ResponseEntity.ok(trainingContentService.getAllTrainings(pageable));
    }

    @Operation(summary = "Delete Training", description = "Soft deletes a training definition.")
    @DeleteMapping("/trainings/{id}")
    public ResponseEntity<Void> deleteTraining(@Parameter(description = "Training UUID") @PathVariable UUID id) {
        trainingContentService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add Module", description = "Adds a new module (Video/PDF/Quiz) to an existing training.")
    @PostMapping("/trainings/{trainingId}/modules")
    public ResponseEntity<TrainingResponseDto> addModule(
            @Parameter(description = "Training UUID") @PathVariable UUID trainingId,
            @Valid @RequestBody CreateModuleRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.addModuleToTraining(trainingId, request));
    }

    @Operation(summary = "Get Module", description = "Retrieves details of a specific module.")
    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ModuleDto> getModule(@Parameter(description = "Module UUID") @PathVariable UUID moduleId) {
        return ResponseEntity.ok(trainingContentService.getModule(moduleId));
    }

    @Operation(summary = "Update Module", description = "Updates an existing module.")
    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<TrainingResponseDto> updateModule(
            @Parameter(description = "Module UUID") @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateModuleRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateModule(moduleId, request));
    }

    @Operation(summary = "Delete Module", description = "Removes a module from a training.")
    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<TrainingResponseDto> deleteModule(@Parameter(description = "Module UUID") @PathVariable UUID moduleId) {
        return ResponseEntity.ok(trainingContentService.deleteModule(moduleId));
    }

    @Operation(summary = "Add Question", description = "Adds a question to a specific Quiz module.")
    @PostMapping("/modules/{moduleId}/questions")
    public ResponseEntity<TrainingResponseDto> addQuestion(
            @Parameter(description = "Module UUID (must be of type QUIZ)") @PathVariable UUID moduleId,
            @Valid @RequestBody CreateQuestionRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.addQuestionToModule(moduleId, request));
    }

    @Operation(summary = "Get Question", description = "Retrieves details of a specific question.")
    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionDto> getQuestion(@Parameter(description = "Question UUID") @PathVariable UUID questionId) {
        return ResponseEntity.ok(trainingContentService.getQuestion(questionId));
    }

    @Operation(summary = "Update Question", description = "Updates a question and its options.")
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<TrainingResponseDto> updateQuestion(
            @Parameter(description = "Question UUID") @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateQuestion(questionId, request));
    }

    @Operation(summary = "Delete Question", description = "Removes a question from a module.")
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<TrainingResponseDto> deleteQuestion(@Parameter(description = "Question UUID") @PathVariable UUID questionId) {
        return ResponseEntity.ok(trainingContentService.deleteQuestion(questionId));
    }

    @Operation(summary = "Get All Users", description = "Retrieves a paginated list of all users.")
    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("surname"));
        return ResponseEntity.ok(userService.getAllUsersSummary(pageable));
    }

    @Operation(summary = "Get User Details", description = "Retrieves detailed information about a specific user.")
    @GetMapping("/users/{id}")
    public ResponseEntity<UserSummaryDto> getUserById(@Parameter(description = "User UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserSummaryById(id));
    }

    @Operation(summary = "Change User Status", description = "Updates user status (e.g. BLOCK, ACTIVATE).")
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<String> changeUserStatus(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "New status") @RequestParam UserStatus status
    ) {
        adminService.changeUserStatus(userId, status);
        return ResponseEntity.ok(AppMessage.USER_STATUS_CHANGED.name());
    }

    @Operation(summary = "Preview Pass", description = "Generates a PDF pass preview for a user (Admin override).")
    @GetMapping("/users/{userId}/pass-preview")
    public ResponseEntity<byte[]> generatePass(@Parameter(description = "User UUID") @PathVariable UUID userId) {
        byte[] pdfBytes = adminService.generatePassPdf(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=pass.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @Operation(summary = "Get User Trainings", description = "Retrieves all trainings assigned to a specific user.")
    @GetMapping("/users/{userId}/trainings")
    public ResponseEntity<Page<UserTrainingDto>> getUserTrainings(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(trainingProgressService.getUserTrainingsByUserId(userId, pageable));
    }

    @Operation(summary = "Assign Training", description = "Manually assigns a training to a user.")
    @PostMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<String> assignTraining(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Training UUID") @PathVariable UUID trainingId
    ) {
        trainingProgressService.assignTrainingToUser(userId, trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_ASSIGNED.name());
    }

    @Operation(summary = "Unassign Training", description = "Removes a training assignment from a user.")
    @DeleteMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<Void> unassignTraining(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Training UUID") @PathVariable UUID trainingId
    ) {
        trainingProgressService.unassignTrainingFromUser(userId, trainingId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign Trainings by Level", description = "Assigns all trainings required for a specific security clearance level.")
    @PostMapping("/users/{userId}/assign-level/{level}")
    public ResponseEntity<String> assignTrainingsByLevel(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Target Security Level (1-4)") @PathVariable int level
    ) {
        trainingProgressService.assignTrainingsByLevelToUser(userId, level);
        return ResponseEntity.ok(AppMessage.TRAININGS_ASSIGNED_BULK.name());
    }

    @Operation(summary = "Revoke Completion", description = "Invalidates the completion of a specific training for a user (forces retake).")
    @PostMapping("/users/{userId}/trainings/{trainingId}/revoke-completion")
    public ResponseEntity<String> revokeCompletion(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Training UUID") @PathVariable UUID trainingId
    ) {
        trainingProgressService.revokeTrainingCompletion(userId, trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_REVOKED.name());
    }

    @Operation(summary = "Get Audit Logs", description = "Retrieves system-wide audit logs.")
    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(auditLogService.getAllLogs(pageable));
    }
}