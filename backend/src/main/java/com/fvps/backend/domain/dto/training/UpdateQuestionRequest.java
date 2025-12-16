package com.fvps.backend.domain.dto.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateQuestionRequest {

    @Schema(description = "Updated question text", example = "Where is the assembly point?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Question text cannot be empty")
    private String questionText;

    @Schema(description = "Updated options", example = "[\"Gate A\", \"Gate B\", \"Parking\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Options are required")
    private List<String> options;

    @Schema(description = "Updated correct option index", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Correct option index cannot be empty")
    private Integer correctOptionIndex;

    @Schema(description = "Optimistic locking version", example = "1")
    private Long version;

    @Schema(description = "Force module progress reset", example = "true")
    private boolean resetProgress;
}