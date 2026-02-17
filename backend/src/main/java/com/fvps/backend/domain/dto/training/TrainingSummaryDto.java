package com.fvps.backend.domain.dto.training;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSummaryDto {
    @Schema(description = "Training UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Training title", example = "OHS Level 1")
    private String title;

    @Schema(description = "Description", example = "Basic safety training")
    private String description;

    @Schema(description = "Training Type", example = "OHS")
    private com.fvps.backend.domain.enums.TrainingType type;

    @Schema(description = "Validity in days", example = "365")
    private int validityPeriodDays;

    @Schema(description = "Passing threshold", example = "0.8")
    private double passingThreshold;

    @Schema(description = "Security level granted", example = "1")
    private int securityLevel;

    @Schema(description = "Is excluded from score calculation", example = "false")
    private boolean excludedFromScore;

    @Schema(description = "Overall passed status", example = "true")
    private boolean passed;

    @Schema(description = "Completion date", example = "2023-10-25T14:30:00")
    private LocalDateTime completedAt;

    // Summary for each quiz in the training
    @Schema(description = "Detailed results for each quiz module")
    private List<ModuleResultDto> quizResults;

}