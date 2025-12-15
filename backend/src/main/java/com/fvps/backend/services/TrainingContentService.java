package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.Training;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TrainingContentService {
    TrainingResponseDto createTraining(CreateTrainingRequest request);

    TrainingResponseDto updateTraining(UUID id, CreateTrainingRequest request);

    void deleteTraining(UUID id);

    TrainingResponseDto getTrainingDetails(UUID id);

    Training getTrainingById(UUID id);

    Page<TrainingSummaryDto> getAllTrainings(Pageable pageable);

    TrainingResponseDto addModuleToTraining(UUID trainingId, CreateModuleRequest request);

    TrainingResponseDto updateModule(UUID moduleId, UpdateModuleRequest request);

    TrainingResponseDto deleteModule(UUID moduleId);

    ModuleDto getModule(UUID moduleId);

    TrainingResponseDto addQuestionToModule(UUID moduleId, CreateQuestionRequest request);

    TrainingResponseDto updateQuestion(UUID questionId, UpdateQuestionRequest request);

    TrainingResponseDto deleteQuestion(UUID questionId);

    QuestionDto getQuestion(UUID questionId);
}