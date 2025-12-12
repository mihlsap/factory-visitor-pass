package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.Training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TrainingService {
    TrainingResponseDto createTraining(CreateTrainingRequest request);

    TrainingResponseDto addModuleToTraining(UUID trainingId, CreateModuleRequest request);

    TrainingResponseDto addQuestionToModule(UUID moduleId, CreateQuestionRequest request);

    TrainingResponseDto getTrainingDetails(UUID id);

    TrainingResponseDto updateTraining(UUID id, CreateTrainingRequest request);

    Page<TrainingSummaryDto> getAllTrainings(Pageable pageable);

    void deleteTraining(UUID id);

    void assignTrainingToUser(UUID userId, UUID trainingId);

    Page<UserTrainingDto> getUserTrainings(String userEmail, Pageable pageable);

    void completeModule(String userEmail, UUID trainingId, UUID moduleId);

    boolean submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission);

    List<UserTrainingDto> getValidTrainingsForUser(UUID userId);

    Page<UserTrainingDto> getUserTrainingsByUserId(UUID userId, Pageable pageable);

    Training getTrainingById(UUID id);

    TrainingResponseDto updateModule(UUID moduleId, UpdateModuleRequest request);

    TrainingResponseDto deleteModule(UUID moduleId);

    TrainingResponseDto updateQuestion(UUID questionId, UpdateQuestionRequest request);

    TrainingResponseDto deleteQuestion(UUID questionId);

    ModuleDto getModule(UUID moduleId);

    QuestionDto getQuestion(UUID questionId);

    void unassignTrainingFromUser(UUID userId, UUID trainingId);

    void revokeTrainingCompletion(UUID userId, UUID trainingId);
}