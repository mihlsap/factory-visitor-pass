package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.QuizQuestion;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.TrainingModule;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.QuizQuestionRepository;
import com.fvps.backend.repositories.TrainingModuleRepository;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.TrainingContentService;
import com.fvps.backend.services.TrainingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingContentServiceImpl implements TrainingContentService {

    private final TrainingRepository trainingRepository;
    private final TrainingModuleRepository moduleRepository;
    private final QuizQuestionRepository questionRepository;
    private final UserTrainingStatusRepository userTrainingStatusRepository;

    private final AuditLogService auditLogService;
    private final TrainingProgressService progressService;

    @Value("${app.training.default-passing-threshold}")
    private double defaultPassingThreshold;

    @Value("${app.training.default-security-level}")
    private int defaultSecurityLevel;

    @Override
    @Transactional
    public TrainingResponseDto createTraining(CreateTrainingRequest request) {
        Training training = mapToEntity(request);
        Training saved = trainingRepository.save(training);
        auditLogService.logEvent("TRAINING_CREATED", "Training created: " + training.getTitle());
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public TrainingResponseDto updateTraining(UUID id, CreateTrainingRequest request) {
        Training training = getTrainingById(id);

        if (request.getVersion() != null && !request.getVersion().equals(training.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Training version mismatch.");
        }

        int oldValidityDays = training.getValidityPeriodDays();
        int newValidityDays = request.getValidityPeriodDays();
        boolean validityChanged = oldValidityDays != newValidityDays;

        training.setTitle(request.getTitle());
        training.setDescription(request.getDescription());
        training.setType(request.getType());
        training.setValidityPeriodDays(newValidityDays);
        if (request.getPassingThreshold() != null) training.setPassingThreshold(request.getPassingThreshold());
        if (request.getSecurityLevel() > 0) training.setSecurityLevel(request.getSecurityLevel());

        Training saved = trainingRepository.save(training);

        if (request.isResetProgress()) {
            progressService.resetProgressForTraining(saved);
        } else if (validityChanged) {
            List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(id);
            int updatedCount = 0;
            for (UserTrainingStatus status : statuses) {
                if (status.getStatus() == ProgressStatus.COMPLETED && status.getCompletedAt() != null && !status.isPassRevoked()) {
                    status.setValidUntil(status.getCompletedAt().plusDays(newValidityDays));
                    updatedCount++;
                }
            }
            if (updatedCount > 0) {
                userTrainingStatusRepository.saveAll(statuses);
                auditLogService.logEvent("TRAINING_VALIDITY_RECALCULATED", "Recalculated validity for " + updatedCount + " users.");
            }
        }

        auditLogService.logEvent("TRAINING_UPDATED", "Training updated: " + training.getTitle());
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public void deleteTraining(UUID id) {
        Training training = getTrainingById(id);
        userTrainingStatusRepository.deleteByTrainingId(id);
        trainingRepository.delete(training);
        auditLogService.logEvent("TRAINING_DELETED", "Deleted training: " + training.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingResponseDto getTrainingDetails(UUID id) {
        return mapToDto(getTrainingById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Training getTrainingById(UUID id) {
        return trainingRepository.findById(id).orElseThrow(() -> new RuntimeException("Training not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TrainingSummaryDto> getAllTrainings(Pageable pageable) {
        return trainingRepository.findAll(pageable).map(this::mapToSummaryDto);
    }

    @Override
    @Transactional
    public TrainingResponseDto addModuleToTraining(UUID trainingId, CreateModuleRequest request) {
        Training training = getTrainingById(trainingId);
        TrainingModule module = mapModuleToEntity(request);
        module.setTraining(training);

        if (training.getModules() == null) training.setModules(new java.util.ArrayList<>());
        List<TrainingModule> modules = training.getModules();

        if (request.getOrderIndex() != null && request.getOrderIndex() >= 0 && request.getOrderIndex() < modules.size()) {
            modules.add(request.getOrderIndex(), module);
        } else {
            modules.add(module);
        }
        reindexModules(modules);
        Training saved = trainingRepository.save(training);

        int targetIndex = module.getOrderIndex();
        TrainingModule newModule = saved.getModules().stream().filter(m -> m.getOrderIndex() == targetIndex).findFirst().orElse(saved.getModules().getLast());

        if (request.isResetProgress()) {
            progressService.resetProgressForModule(newModule);
        }

        if (saved.getModules().size() == 1) {
            List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(trainingId);
            for (UserTrainingStatus s : statuses) {
                if (s.getStatus() == ProgressStatus.NOT_STARTED && s.getCurrentModule() == null) {
                    s.setCurrentModule(newModule);
                    userTrainingStatusRepository.save(s);
                }
            }
        }

        auditLogService.logEvent("MODULE_ADDED", "Added module to: " + training.getTitle());
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public TrainingResponseDto updateModule(UUID moduleId, UpdateModuleRequest request) {
        TrainingModule module = moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found"));

        if (request.getVersion() != null && !request.getVersion().equals(module.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Module version mismatch.");
        }

        module.setTitle(request.getTitle());
        module.setContentUrl(request.getContentUrl());

        if (request.getType() != null) {
            module.setType(request.getType());
            if (request.getType() != ModuleType.QUIZ) module.getQuestions().clear();
        }

        Training training = module.getTraining();
        List<TrainingModule> modules = training.getModules();

        if (request.getOrderIndex() != null && request.getOrderIndex() >= 0 && request.getOrderIndex() < modules.size() && request.getOrderIndex() != module.getOrderIndex()) {
            modules.remove(module);
            modules.add(request.getOrderIndex(), module);
            reindexModules(modules);
            trainingRepository.save(training);
        } else {
            moduleRepository.save(module);
        }

        if (request.isResetProgress()) {
            progressService.resetProgressForModule(module);
        }

        auditLogService.logEvent("MODULE_UPDATED", "Module updated: " + module.getTitle());
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional
    public TrainingResponseDto deleteModule(UUID moduleId) {
        TrainingModule moduleToDelete = moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found"));
        Training training = moduleToDelete.getTraining();

        boolean activeUsers = userTrainingStatusRepository.findAllByTrainingId(training.getId()).stream()
                .anyMatch(s -> s.getCurrentModule() != null && s.getCurrentModule().getId().equals(moduleId));

        if (activeUsers) throw new IllegalStateException("Cannot delete active module.");

        training.getModules().remove(moduleToDelete);
        moduleRepository.delete(moduleToDelete);
        reindexModules(training.getModules());
        trainingRepository.save(training);

        auditLogService.logEvent("MODULE_DELETED", "Deleted module: " + moduleToDelete.getTitle());
        return mapToDto(training);
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleDto getModule(UUID moduleId) {
        return mapModuleToDto(moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found")));
    }

    @Override
    @Transactional
    public TrainingResponseDto addQuestionToModule(UUID moduleId, CreateQuestionRequest request) {
        TrainingModule module = moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found"));
        if (module.getType() != ModuleType.QUIZ) throw new IllegalStateException("Not a QUIZ module.");

        QuizQuestion question = mapQuestionToEntity(request);
        question.setModule(module);
        question.setOrderIndex(module.getQuestions() != null ? module.getQuestions().size() : 0);

        if (module.getQuestions() == null) module.setQuestions(new java.util.ArrayList<>());
        module.getQuestions().add(question);

        moduleRepository.save(module);

        if (request.isResetProgress()) {
            progressService.resetProgressForModule(module);
        }

        auditLogService.logEvent("QUESTION_ADDED", "Added question to: " + module.getTitle());
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional
    public TrainingResponseDto updateQuestion(UUID questionId, UpdateQuestionRequest request) {
        QuizQuestion question = questionRepository.findById(questionId).orElseThrow(() -> new RuntimeException("Question not found"));

        if (request.getVersion() != null && !request.getVersion().equals(question.getVersion()))
            throw new org.springframework.dao.OptimisticLockingFailureException("Question version mismatch.");

        question.setQuestionText(request.getQuestionText());
        question.setOptions(request.getOptions());
        question.setCorrectOptionIndex(request.getCorrectOptionIndex());

        questionRepository.save(question);

        if (request.isResetProgress()) {
            progressService.resetProgressForModule(question.getModule());
        }

        auditLogService.logEvent("QUESTION_UPDATED", "Updated question ID: " + questionId);
        return mapToDto(question.getModule().getTraining());
    }

    @Override
    @Transactional
    public TrainingResponseDto deleteQuestion(UUID questionId) {
        QuizQuestion question = questionRepository.findById(questionId).orElseThrow(() -> new RuntimeException("Question not found"));
        TrainingModule module = question.getModule();
        module.getQuestions().remove(question);
        questionRepository.delete(question);
        auditLogService.logEvent("QUESTION_DELETED", "Deleted question ID: " + questionId);
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionDto getQuestion(UUID questionId) {
        return mapQuestionToDto(questionRepository.findById(questionId).orElseThrow(() -> new RuntimeException("Question not found")));
    }

    private Training mapToEntity(CreateTrainingRequest req) {
        Training training = Training.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .type(req.getType())
                .validityPeriodDays(req.getValidityPeriodDays())
                .passingThreshold(req.getPassingThreshold() != null ? req.getPassingThreshold() : defaultPassingThreshold)
                .securityLevel(req.getSecurityLevel() > 0 ? req.getSecurityLevel() : defaultSecurityLevel)
                .modules(new java.util.ArrayList<>())
                .build();
        if (req.getModules() != null) {
            req.getModules().forEach(mReq -> {
                TrainingModule m = mapModuleToEntity(mReq);
                m.setTraining(training);
                m.setOrderIndex(training.getModules().size());
                training.getModules().add(m);
            });
        }
        return training;
    }

    private TrainingModule mapModuleToEntity(CreateModuleRequest req) {
        TrainingModule module = TrainingModule.builder().title(req.getTitle()).type(req.getType()).contentUrl(req.getContentUrl()).questions(new java.util.ArrayList<>()).build();
        if (req.getQuestions() != null) {
            req.getQuestions().forEach(qReq -> {
                QuizQuestion q = mapQuestionToEntity(qReq);
                q.setModule(module);
                q.setOrderIndex(module.getQuestions().size());
                module.getQuestions().add(q);
            });
        }
        return module;
    }

    private QuizQuestion mapQuestionToEntity(CreateQuestionRequest req) {
        return QuizQuestion.builder().questionText(req.getQuestionText()).options(req.getOptions()).correctOptionIndex(req.getCorrectOptionIndex()).build();
    }

    private TrainingResponseDto mapToDto(Training entity) {
        return TrainingResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .type(entity.getType())
                .passingThreshold(entity.getPassingThreshold())
                .validityPeriodDays(entity.getValidityPeriodDays())
                .version(entity.getVersion())
                .securityLevel(entity.getSecurityLevel())
                .modules(entity.getModules() != null ? entity.getModules().stream().map(this::mapModuleToDto).toList() : List.of())
                .build();
    }

    private ModuleDto mapModuleToDto(TrainingModule entity) {
        return ModuleDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .orderIndex(entity.getOrderIndex())
                .type(entity.getType())
                .contentUrl(entity.getContentUrl())
                .version(entity.getVersion())
                .questions(entity.getQuestions() != null ? entity.getQuestions().stream().map(this::mapQuestionToDto).toList() : List.of())
                .build();
    }

    private QuestionDto mapQuestionToDto(QuizQuestion entity) {
        return QuestionDto.builder()
                .id(entity.getId())
                .questionText(entity.getQuestionText())
                .options(entity.getOptions())
                .version(entity.getVersion())
                .correctOptionIndex(entity.getCorrectOptionIndex())
                .build();
    }

    private TrainingSummaryDto mapToSummaryDto(Training entity) {
        return TrainingSummaryDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .type(entity.getType())
                .validityPeriodDays(entity.getValidityPeriodDays())
                .passingThreshold(entity.getPassingThreshold())
                .securityLevel(entity.getSecurityLevel())
                .build();
    }

    private void reindexModules(List<TrainingModule> modules) {
        for (int i = 0; i < modules.size(); i++) {
            modules.get(i).setOrderIndex(i);
        }
    }
}