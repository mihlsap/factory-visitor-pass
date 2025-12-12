package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.TrainingType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateTrainingRequest {
    @NotBlank(message = "Title is required.")
    private String title;

    @NotBlank(message = "Description is required.")
    private String description;

    @NotNull(message = "Type of training is required.")
    private TrainingType type;

    @Min(value = 1, message = "Validity period has to be at least one day.")
    private int validityPeriodDays;

    @DecimalMin(value = "0.0", message = "Passing threshold cannot be less than 0.0 (0%)")
    @DecimalMax(value = "1.0", message = "Passing threshold cannot be more than 1.0 (100%)")
    private Double passingThreshold;

    private List<CreateModuleRequest> modules;

    private Long version;
}