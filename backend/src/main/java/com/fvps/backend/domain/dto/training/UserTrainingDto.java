package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.enums.ProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserTrainingDto {
    private UUID id; // ID przypisania (UserTrainingStatus)
    private Training training; // Pełne dane szkolenia
    private ProgressStatus status;
    private int currentModuleIndex;
    private Double quizScore;
    private LocalDateTime completedAt;
    private LocalDateTime validUntil;
    private boolean isPassRevoked;
}