package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingServiceImpl implements TrainingService {

    private final TrainingRepository trainingRepository;

    @Override
    @Transactional
    public Training createTraining(Training training) {
        // Jeśli tworzysz szkolenie z modułami, musisz ustawić relację zwrotną (parent)
        if (training.getModules() != null) {
            training.getModules().forEach(module -> {
                module.setTraining(training);
                if (module.getQuestions() != null) {
                    module.getQuestions().forEach(q -> q.setModule(module));
                }
            });
        }
        return trainingRepository.save(training);
    }

    @Override
    public List<Training> getAllTrainings() {
        return trainingRepository.findAll();
    }

    @Override
    public Training getTrainingById(UUID id) {
        return trainingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Szkolenie nie znalezione"));
    }

    @Override
    public void deleteTraining(UUID id) {
        trainingRepository.deleteById(id);
    }
}