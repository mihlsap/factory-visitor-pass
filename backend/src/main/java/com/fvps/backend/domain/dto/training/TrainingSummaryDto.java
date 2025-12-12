package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.TrainingType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TrainingSummaryDto {
    private UUID id;
    private String title;
    private String description;
    private TrainingType type;
    private int validityPeriodDays;
    private Double passingThreshold;
}