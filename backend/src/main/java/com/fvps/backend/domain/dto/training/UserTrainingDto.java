package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.ProgressStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserTrainingDto {

    @Schema(description = "UserTrainingStatus ID", example = "b123...")
    private UUID id;

    @Schema(description = "Training details")
    private TrainingSummaryDto training;

    @Schema(description = "Current progress status", example = "IN_PROGRESS")
    private ProgressStatus status;

    @Schema(description = "Index of the current active module", example = "2")
    private int currentModuleIndex;

    @Schema(description = "Total number of modules from the training definition", example = "3")
    private int totalModules;

    @Schema(description = "Calculated progress percentage (0-100)", example = "33")
    private int progressPercentage;

    @Schema(description = "Completion timestamp", example = "2023-10-25T14:30:00")
    private LocalDateTime completedAt;

    @Schema(description = "Training validity expiration date", example = "2024-10-25T14:30:00")
    private LocalDateTime validUntil;

}