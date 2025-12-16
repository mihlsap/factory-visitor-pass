package com.fvps.backend.domain.dto.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateQuestionRequest {

    @Schema(description = "Question text", example = "What should you do in case of fire?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Question text is required")
    private String questionText;

    @Schema(description = "List of possible answers", example = "[\"Run\", \"Use extinguisher\", \"Call 112\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Options are required")
    private List<String> options;

    @Schema(description = "Index of the correct option (0-based)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Correct option index is required")
    private Integer correctOptionIndex;

    @Schema(description = "Optimistic locking version", example = "1")
    private Long version;

    @Schema(description = "Reset progress flag", example = "false")
    private boolean resetProgress;
}