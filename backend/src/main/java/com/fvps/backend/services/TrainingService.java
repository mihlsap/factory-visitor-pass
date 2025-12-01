package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.Training;
import java.util.List;
import java.util.UUID;

public interface TrainingService {
    Training createTraining(Training training);
    List<Training> getAllTrainings();
    Training getTrainingById(UUID id);
    void deleteTraining(UUID id);
    void assignTrainingToUser(UUID userId, UUID trainingId);
    List<UserTrainingDto> getUserTrainings(String userEmail);
    void completeModule(String userEmail, UUID trainingId, UUID moduleId);
    boolean submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission);
    List<UserTrainingDto> getValidTrainingsForUser(UUID userId);
}