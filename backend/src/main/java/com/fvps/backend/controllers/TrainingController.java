package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.services.TrainingProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/trainings")
@RequiredArgsConstructor
@Tag(name = "Training Progress", description = "Endpoints for users to view assigned trainings and track progress (modules, quizzes).")
@SecurityRequirement(name = "bearerAuth")
public class TrainingController {

    private final TrainingProgressService trainingProgressService;

    @Operation(summary = "Get My Trainings", description = "Retrieves a paginated list of trainings assigned to the current user.")
    @GetMapping("/my")
    public ResponseEntity<Page<UserTrainingDto>> getMyTrainings(
            @Parameter(hidden = true) Authentication authentication,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(trainingProgressService.getUserTrainings(authentication.getName(), pageable));
    }

    @Operation(summary = "Complete Module", description = "Marks a specific module (Video/PDF) as completed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module marked as completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid module sequence or already completed"),
            @ApiResponse(responseCode = "404", description = "Training or module not found")
    })
    @PostMapping("/{trainingId}/module/{moduleId}/complete")
    public ResponseEntity<String> completeModule(
            @Parameter(hidden = true) Authentication authentication,

            @Parameter(description = "Training UUID", required = true)
            @PathVariable UUID trainingId,

            @Parameter(description = "Module UUID", required = true)
            @PathVariable UUID moduleId
    ) {
        trainingProgressService.completeModule(authentication.getName(), trainingId, moduleId);
        return ResponseEntity.ok(AppMessage.MODULE_COMPLETED.name());
    }

    @Operation(summary = "Submit Quiz", description = "Submits answers for a Quiz module and evaluates the result.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quiz submitted. Returns 'QUIZ_PASSED' or 'QUIZ_FAILED' message.",
                    content = @Content(schema = @Schema(type = "string", example = "QUIZ_PASSED"))),
            @ApiResponse(responseCode = "400", description = "Invalid submission (e.g. missing answers)"),
            @ApiResponse(responseCode = "404", description = "Training or module not found")
    })
    @PostMapping("/{trainingId}/module/{moduleId}/submit-quiz")
    public ResponseEntity<String> submitQuiz(
            @Parameter(hidden = true) Authentication authentication,

            @Parameter(description = "Training UUID", required = true)
            @PathVariable UUID trainingId,

            @Parameter(description = "Module UUID (must be of type QUIZ)", required = true)
            @PathVariable UUID moduleId,

            @Parameter(description = "Map of Question IDs and selected Option IDs", required = true)
            @RequestBody QuizSubmissionDto submission
    ) {
        boolean passed = trainingProgressService.submitQuiz(authentication.getName(), trainingId, moduleId, submission);

        if (passed) {
            return ResponseEntity.ok(AppMessage.QUIZ_PASSED.name());
        } else {
            return ResponseEntity.ok(AppMessage.QUIZ_FAILED.name());
        }
    }
}