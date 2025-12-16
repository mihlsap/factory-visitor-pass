package com.fvps.backend.domain.dto.training;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmissionDto {

    @Schema(
            description = "Map of answers where Key is Question UUID and Value is selected Option Index",
            example = "{\"770e8400-e29b-41d4-a716-446655440099\": 1, \"880e8400-...\": 0}"
    )
    private Map<UUID, Integer> answers;
}