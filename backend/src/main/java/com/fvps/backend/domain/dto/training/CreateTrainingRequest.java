package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.TrainingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateTrainingRequest {

    @Schema(description = "Training title", example = "OHS Safety - Level 1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Title is required.")
    private String title;

    @Schema(description = "Detailed description of the training content", example = "Basic safety rules for factory visitors.", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Description is required.")
    private String description;

    @Schema(description = "Type/Category of the training", example = "OHS", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Type of training is required.")
    private TrainingType type;

    @Schema(description = "Number of days the training is valid after completion", example = "365", minimum = "1")
    @Min(value = 1, message = "Validity period has to be at least one day.")
    private int validityPeriodDays;

    @Schema(description = "Score required to pass the quiz (0.0 to 1.0)", example = "0.8")
    @DecimalMin(value = "0.0", message = "Passing threshold cannot be less than 0.0 (0%)")
    @DecimalMax(value = "1.0", message = "Passing threshold cannot be more than 1.0 (100%)")
    private Double passingThreshold;

    @Schema(description = "List of modules (videos, slides, quizzes) to be created with the training")
    private List<CreateModuleRequest> modules;

    @Schema(description = "Optimistic locking version (optional for creation)", example = "1")
    private Long version;

    @Schema(description = "Security clearance level granted upon completion (1-4)", example = "1", minimum = "1", maximum = "4")
    @Min(value = 1, message = "Security level must be at least 1")
    @Max(value = 4, message = "Security level cannot be higher than 4")
    private int securityLevel;

    @Schema(description = "If true, forces users to retake the training even if completed", example = "false")
    private boolean resetProgress;
}