package com.fvps.backend.domain.dto.training;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class QuestionDto {

    @Schema(description = "Question UUID", example = "770e8400-e29b-41d4-a716-446655440099")
    private UUID id;

    @Schema(description = "Question content", example = "What is the emergency number?")
    private String questionText;

    @Schema(description = "List of options", example = "[\"999\", \"112\", \"911\"]")
    private List<String> options;

    @Schema(description = "Index of correct answer (hidden in some contexts)", example = "1")
    private int correctOptionIndex;

    @Schema(description = "Version", example = "1")
    private Long version;
}