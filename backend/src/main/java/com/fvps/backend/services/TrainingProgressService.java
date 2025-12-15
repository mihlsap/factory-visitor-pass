package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.TrainingModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TrainingProgressService {
    void assignTrainingToUser(UUID userId, UUID trainingId);

    void unassignTrainingFromUser(UUID userId, UUID trainingId);

    Page<UserTrainingDto> getUserTrainings(String userEmail, Pageable pageable);

    Page<UserTrainingDto> getUserTrainingsByUserId(UUID userId, Pageable pageable);

    List<UserTrainingDto> getValidTrainingsForUser(UUID userId);

    void completeModule(String userEmail, UUID trainingId, UUID moduleId);

    boolean submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission);

    void revokeTrainingCompletion(UUID userId, UUID trainingId);

    void resetProgressForTraining(Training training);

    void resetProgressForModule(TrainingModule module);

    void assignTrainingsByLevelToUser(UUID userId, int level);
}