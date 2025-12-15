package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.ModuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateModuleRequest {

    @Schema(description = "Module title", example = "Introduction Video", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Module title is required")
    private String title;

    @Schema(description = "Module type", example = "VIDEO", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Module type is required")
    private ModuleType type;

    @Schema(description = "URL to content (video link or PDF path)", example = "https://youtube.com/watch?v=...")
    private String contentUrl;

    @Schema(description = "List of questions (only for QUIZ type)")
    private List<CreateQuestionRequest> questions;

    @Schema(description = "Order index in the training sequence", example = "0")
    @Min(0)
    private Integer orderIndex;

    @Schema(description = "If true, resets progress for this module for all users", example = "false")
    private boolean resetProgress;
}