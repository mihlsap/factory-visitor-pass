package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.ProgressStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
public class TrainingAssignmentDto {
    @Schema(description = "User UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "User's first name", example = "John")
    private String name;

    @Schema(description = "User's last name", example = "Doe")
    private String surname;

    @Schema(description = "User's email", example = "john.doe@fvps.com")
    private String email;

    @Schema(description = "Training completion status", example = "COMPLETED")
    private ProgressStatus status;

    @Schema(description = "Date of completion (if applicable)", example = "2023-10-25T14:30:00")
    private LocalDateTime completedAt;
}