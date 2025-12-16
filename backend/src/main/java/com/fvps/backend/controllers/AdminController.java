package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.CreateTrainingRequest;
import com.fvps.backend.domain.dto.training.TrainingResponseDto;
import com.fvps.backend.domain.dto.training.TrainingSummaryDto;
import com.fvps.backend.domain.dto.user.UserSummaryDto;
import com.fvps.backend.domain.entities.AuditLog;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Admin Panel", description = "Endpoints for managing users, trainings, system logs, and user statuses.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TrainingContentService trainingContentService;
    private final TrainingProgressService trainingProgressService;

    @Operation(summary = "Get All Users", description = "Retrieves a paginated list of all users registered in the system.")
    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryDto>> getAllUsers(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("surname"));
        return ResponseEntity.ok(userService.getAllUsersSummary(pageable));
    }

    @Operation(summary = "Get User Details", description = "Retrieves detailed information about a specific user by their ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User details returned"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserSummaryDto> getUserDetails(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(userService.getUserSummaryById(userId));
    }

    @Operation(summary = "Change User Status", description = "Updates the status of a user (e.g., BLOCK, ACTIVATE, DELETE).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<String> changeUserStatus(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userId,

            @Parameter(description = "New status to apply", required = true)
            @RequestParam UserStatus status
    ) {
        adminService.changeUserStatus(userId, status);
        return ResponseEntity.ok(AppMessage.USER_STATUS_CHANGED.name());
    }

    @Operation(summary = "Assign Training", description = "Manually assigns a specific training to a user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Training assigned successfully"),
            @ApiResponse(responseCode = "400", description = "Training already assigned or invalid"),
            @ApiResponse(responseCode = "404", description = "User or Training not found")
    })
    @PostMapping("/users/{userId}/assign/{trainingId}")
    public ResponseEntity<String> assignTraining(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userId,

            @Parameter(description = "Training UUID", required = true)
            @PathVariable UUID trainingId
    ) {
        trainingProgressService.assignTrainingToUser(userId, trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_ASSIGNED.name());
    }

    @Operation(summary = "Create Training", description = "Creates a new training with modules and questions.")
    @ApiResponse(responseCode = "200", description = "Training created successfully", content = @Content(schema = @Schema(implementation = TrainingResponseDto.class)))
    @PostMapping("/trainings")
    public ResponseEntity<TrainingResponseDto> createTraining(
            @Valid @RequestBody CreateTrainingRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.createTraining(request));
    }

    @Operation(summary = "Get All Trainings", description = "Retrieves a paginated list of all available trainings.")
    @GetMapping("/trainings")
    public ResponseEntity<Page<TrainingSummaryDto>> getAllTrainings(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("title"));
        return ResponseEntity.ok(trainingContentService.getAllTrainings(pageable));
    }

    @Operation(summary = "Update Training", description = "Updates an existing training structure, including modules and questions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Training updated successfully"),
            @ApiResponse(responseCode = "404", description = "Training not found"),
            @ApiResponse(responseCode = "409", description = "Optimistic locking failure (version mismatch)")
    })
    @PutMapping("/trainings/{trainingId}")
    public ResponseEntity<TrainingResponseDto> updateTraining(
            @Parameter(description = "Training UUID", required = true)
            @PathVariable UUID trainingId,

            @Valid @RequestBody CreateTrainingRequest request
    ) {
        return ResponseEntity.ok(trainingContentService.updateTraining(trainingId, request));
    }

    @Operation(summary = "Delete Training", description = "Soft deletes a training. It will no longer be assignable to new users.")
    @DeleteMapping("/trainings/{trainingId}")
    public ResponseEntity<String> deleteTraining(
            @Parameter(description = "Training UUID", required = true)
            @PathVariable UUID trainingId
    ) {
        trainingContentService.deleteTraining(trainingId);
        return ResponseEntity.ok(AppMessage.TRAINING_DELETED.name());
    }

    @Operation(summary = "Get Audit Logs", description = "Retrieves system audit logs for monitoring user activities.")
    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(auditLogService.getAllLogs(pageable));
    }

    @Operation(summary = "Preview User Pass", description = "Generates a PDF pass preview for a specific user (Admin only feature).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF file returned", content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "User does not have valid trainings")
    })
    @GetMapping("/users/{userId}/pass-preview")
    public ResponseEntity<byte[]> generatePass(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userId
    ) {
        byte[] pdfBytes = adminService.generatePassPdf(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=pass.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}