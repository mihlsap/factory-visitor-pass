package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.TrainingType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TrainingSummaryDto {

    @Schema(description = "Training UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID id;

    @Schema(description = "Training title", example = "OHS Safety - Level 1")
    private String title;

    @Schema(description = "Short description", example = "Basic safety rules...")
    private String description;

    @Schema(description = "Type", example = "OHS")
    private TrainingType type;

    @Schema(description = "Validity in days", example = "365")
    private int validityPeriodDays;

    @Schema(description = "Passing threshold", example = "0.8")
    private Double passingThreshold;

    @Schema(description = "Security level", example = "1")
    private int securityLevel;
}