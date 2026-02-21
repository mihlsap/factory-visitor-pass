package com.fvps.backend.domain.dto.training;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fvps.backend.domain.enums.ModuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ModuleDto {

    @Schema(description = "Module UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Module title", example = "Introduction")
    private String title;

    @Schema(description = "Order in the training path", example = "0")
    private int orderIndex;

    @Schema(description = "Type", example = "VIDEO")
    private ModuleType type;

    @Schema(description = "Content URL", example = "https://youtube.com/...")
    private String contentUrl;

    @Schema(description = "Version", example = "1")
    private Long version;

    @Schema(description = "Questions (if type is QUIZ)")
    private List<QuestionDto> questions;

    @Schema(description = "Passing threshold (0.0 - 1.0) for this module", example = "0.8")
    private Double passingThreshold;

    @Schema(description = "Whether the module is passed (for quizzes)", example = "true")
    private Boolean passed;

    @Schema(description = "Indicates if the module has been completed by the user. Null for admin view.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean completed;
}