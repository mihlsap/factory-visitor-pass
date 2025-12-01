package com.fvps.backend.services;

import com.fvps.backend.domain.entities.Training;
import java.util.List;
import java.util.UUID;

public interface TrainingService {
    Training createTraining(Training training);
    List<Training> getAllTrainings();
    Training getTrainingById(UUID id);
    void deleteTraining(UUID id);
}