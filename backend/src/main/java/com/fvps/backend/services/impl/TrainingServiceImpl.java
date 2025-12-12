package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.*;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.*;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.PassService;
import com.fvps.backend.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingServiceImpl implements TrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingModuleRepository moduleRepository;
    private final UserRepository userRepository;
    private final UserTrainingStatusRepository userTrainingStatusRepository;
    private final AuditLogService auditLogService;
    private final QuizQuestionRepository questionRepository;
    private final PassService passService;

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
    public TrainingResponseDto addModuleToTraining(UUID trainingId, CreateModuleRequest request) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        TrainingModule module = mapModuleToEntity(request);
        module.setTraining(training);

        if (training.getModules() == null) {
            training.setModules(new java.util.ArrayList<>());
        }

        List<TrainingModule> modules = training.getModules();

        if (request.getOrderIndex() != null && request.getOrderIndex() >= 0 && request.getOrderIndex() < modules.size()) {
            modules.add(request.getOrderIndex(), module);
        } else {
            modules.add(module);
        }

        reindexModules(modules);

        Training savedTraining = trainingRepository.save(training);

        int targetIndex = module.getOrderIndex();
        TrainingModule newModule = savedTraining.getModules().stream()
                .filter(m -> m.getOrderIndex() == targetIndex)
                .findFirst()
                .orElse(savedTraining.getModules().getLast());


        if (request.isResetProgress()) {
            revokeProgressForModule(newModule);
        }

        if (savedTraining.getModules().size() == 1) {
            List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(trainingId);
            for (UserTrainingStatus status : statuses) {
                if (status.getStatus() == ProgressStatus.NOT_STARTED && status.getCurrentModule() == null) {
                    status.setCurrentModule(newModule);
                    userTrainingStatusRepository.save(status);
                }
            }
        }

        auditLogService.logEvent("MODULE_ADDED", "Added module to training " + training.getTitle());
        return mapToDto(savedTraining);
    }

    @Override
    @Transactional
    public TrainingResponseDto addQuestionToModule(UUID moduleId, CreateQuestionRequest request) {
        TrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));

        if (module.getType() != ModuleType.QUIZ) {
            throw new IllegalStateException("Questions can only be added to QUIZ modules");
        }

        QuizQuestion question = mapQuestionToEntity(request);
        question.setModule(module);

        int newIndex = module.getQuestions() != null ? module.getQuestions().size() : 0;
        question.setOrderIndex(newIndex);

        if (module.getQuestions() == null) {
            module.setQuestions(new java.util.ArrayList<>());
        }
        module.getQuestions().add(question);

        moduleRepository.save(module);

        if (request.isResetProgress()) {
            revokeProgressForModule(module);
        }

        auditLogService.logEvent("QUESTION_ADDED", "Added question to module: " + module.getTitle());
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingResponseDto getTrainingDetails(UUID id) {
        return mapToDto(getTrainingById(id));
    }

    @Override
    @Transactional
    public TrainingResponseDto updateTraining(UUID id, CreateTrainingRequest request) {
        Training training = getTrainingById(id);

        if (request.getVersion() != null && !request.getVersion().equals(training.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Training version mismatch. Client has version " + request.getVersion() +
                            " but DB has " + training.getVersion()
            );
        }

        int oldValidityDays = training.getValidityPeriodDays();
        int newValidityDays = request.getValidityPeriodDays();
        boolean validityChanged = oldValidityDays != newValidityDays;

        training.setTitle(request.getTitle());
        training.setDescription(request.getDescription());
        training.setType(request.getType());
        training.setValidityPeriodDays(newValidityDays);

        if (request.getPassingThreshold() != null) {
            training.setPassingThreshold(request.getPassingThreshold());
        }

        Training saved = trainingRepository.save(training);

        if (validityChanged) {
            List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(id);
            int updatedCount = 0;

            for (UserTrainingStatus status : statuses) {
                if (status.getStatus() == ProgressStatus.COMPLETED && status.getCompletedAt() != null) {
                    LocalDateTime newValidUntil = status.getCompletedAt().plusDays(newValidityDays);

                    if (!status.isPassRevoked()) {
                        status.setValidUntil(newValidUntil);
                        updatedCount++;
                    }
                }
            }
            userTrainingStatusRepository.saveAll(statuses);

            if (updatedCount > 0) {
                auditLogService.logEvent("TRAINING_VALIDITY_RECALCULATED",
                        "Recalculated validity for " + updatedCount + " users in training: " + training.getTitle());
            }
        }

        auditLogService.logEvent("TRAINING_UPDATED", "Training updated: " + training.getTitle());
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TrainingSummaryDto> getAllTrainings(Pageable pageable) {
        return trainingRepository.findAll(pageable)
                .map(this::mapToSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Training getTrainingById(UUID id) {
        return trainingRepository.findById(id).orElseThrow(() -> new RuntimeException("Training not found"));
    }

    @Override
    @Transactional
    public TrainingResponseDto updateModule(UUID moduleId, UpdateModuleRequest request) {
        TrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));

        if (request.getVersion() != null && !request.getVersion().equals(module.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Module version mismatch. Client: " + request.getVersion() + ", DB: " + module.getVersion());
        }

        module.setTitle(request.getTitle());
        module.setContentUrl(request.getContentUrl());

        if (request.getType() != null) {
            module.setType(request.getType());
            if (request.getType() != ModuleType.QUIZ) {
                module.getQuestions().clear();
            }
        }

        Training training = module.getTraining();
        List<TrainingModule> modules = training.getModules();

        if (request.getOrderIndex() != null
                && request.getOrderIndex() >= 0
                && request.getOrderIndex() < modules.size()
                && request.getOrderIndex() != module.getOrderIndex()) {

            modules.remove(module);
            modules.add(request.getOrderIndex(), module);
            reindexModules(modules);

            trainingRepository.save(training);
        } else {
            moduleRepository.save(module);
        }

        if (request.isResetProgress()) {
            revokeProgressForModule(module);
        }

        auditLogService.logEvent("MODULE_UPDATED", "Module updated: " + module.getTitle());
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional
    public TrainingResponseDto deleteModule(UUID moduleId) {
        TrainingModule moduleToDelete = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        Training training = moduleToDelete.getTraining();

        boolean isAnyoneActiveOnThisModule = userTrainingStatusRepository.findAllByTrainingId(training.getId())
                .stream()
                .anyMatch(status -> status.getCurrentModule() != null
                        && status.getCurrentModule().getId().equals(moduleId));

        if (isAnyoneActiveOnThisModule) {
            throw new IllegalStateException("Cannot delete module because users are currently active on it.");
        }

        training.getModules().remove(moduleToDelete);
        moduleRepository.delete(moduleToDelete);

        List<TrainingModule> remainingModules = training.getModules();
        for (int i = 0; i < remainingModules.size(); i++) {
            remainingModules.get(i).setOrderIndex(i);
        }
        trainingRepository.save(training);

        auditLogService.logEvent("MODULE_DELETED", "Deleted module: " + moduleToDelete.getTitle());
        return mapToDto(training);
    }

    @Override
    @Transactional
    public TrainingResponseDto updateQuestion(UUID questionId, UpdateQuestionRequest request) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        if (request.getVersion() != null && !request.getVersion().equals(question.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Question version mismatch. Client: " + request.getVersion() + ", DB: " + question.getVersion());
        }

        question.setQuestionText(request.getQuestionText());
        question.setOptions(request.getOptions());
        question.setCorrectOptionIndex(request.getCorrectOptionIndex());

        questionRepository.save(question);

        if (request.isResetProgress()) {
            revokeProgressForModule(question.getModule());
        }

        auditLogService.logEvent("QUESTION_UPDATED", "Updated question ID: " + questionId);
        return mapToDto(question.getModule().getTraining());
    }

    @Override
    @Transactional
    public TrainingResponseDto deleteQuestion(UUID questionId) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        TrainingModule module = question.getModule();
        module.getQuestions().remove(question);
        questionRepository.delete(question);

        auditLogService.logEvent("QUESTION_DELETED", "Deleted question ID: " + questionId);
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional
    public void deleteTraining(UUID id) {
        Training training = trainingRepository.findById(id).orElseThrow(() -> new RuntimeException("Training not found."));
        userTrainingStatusRepository.deleteByTrainingId(id);
        trainingRepository.delete(training);
        auditLogService.logEvent("TRAINING_DELETED", "Deleted training: " + training.getTitle());
    }

    @Override
    @Transactional
    public void assignTrainingToUser(UUID userId, UUID trainingId) {
        if (userTrainingStatusRepository.existsByUserIdAndTrainingId(userId, trainingId)) {
            throw new IllegalStateException("Training is already assigned to this user.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Training training = trainingRepository.findById(trainingId).orElseThrow(() -> new RuntimeException("Training not found"));

        TrainingModule firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId)
                .orElse(null);

        UserTrainingStatus status = UserTrainingStatus.builder()
                .user(user)
                .training(training)
                .status(ProgressStatus.NOT_STARTED)
                .currentModule(firstModule)
                .build();

        userTrainingStatusRepository.save(status);
        auditLogService.logEvent(userId, "TRAINING_ASSIGNED", "Assigned training: " + training.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserTrainingDto> getUserTrainings(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return userTrainingStatusRepository.findByUserId(user.getId(), pageable)
                .map(this::mapToUserTrainingDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserTrainingDto> getUserTrainingsByUserId(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }

        return userTrainingStatusRepository.findByUserId(userId, pageable)
                .map(this::mapToUserTrainingDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrainingDto> getValidTrainingsForUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }
        return fetchValidTrainingsInternal(userId);
    }

    @Override
    @Transactional
    public void completeModule(String userEmail, UUID trainingId, UUID moduleId) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);

        if (status.getCurrentModule() == null && status.getStatus() == ProgressStatus.NOT_STARTED) {
            var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId)
                    .orElse(null);

            if (firstModule != null) {
                status.setCurrentModule(firstModule);
                userTrainingStatusRepository.save(status);
            }
        }

        if (status.getCurrentModule() == null) {
            if (status.getStatus() == ProgressStatus.COMPLETED) return;
            throw new IllegalArgumentException("Training progress error or already completed.");
        }

        if (!status.getCurrentModule().getId().equals(moduleId)) {
            throw new IllegalArgumentException("Cannot skip modules! Your current module is: " + status.getCurrentModule().getTitle());
        }

        if (status.getCurrentModule().getType() == ModuleType.QUIZ) {
            throw new IllegalArgumentException("Quiz must be solved, cannot be skipped.");
        }

        advanceProgress(status);
        handleCourseCompletion(status);

        auditLogService.logEvent(status.getUser().getId(), "MODULE_COMPLETED", "Completed module (Video/PDF).");
    }

    @Override
    @Transactional
    public boolean submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);
        Training training = status.getTraining();

        if (status.getCurrentModule() == null && status.getStatus() == ProgressStatus.NOT_STARTED) {
            var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId)
                    .orElse(null);
            if (firstModule != null) {
                status.setCurrentModule(firstModule);
                userTrainingStatusRepository.save(status);
            }
        }

        if (status.getCurrentModule() == null) {
            throw new IllegalArgumentException("Training already completed or no active module.");
        }

        if (!status.getCurrentModule().getId().equals(moduleId)) {
            throw new IllegalArgumentException("Attempt to solve quiz from another module.");
        }

        TrainingModule currentModule = status.getCurrentModule();

        if (!currentModule.getId().equals(moduleId) || currentModule.getType() != ModuleType.QUIZ) {
            throw new IllegalArgumentException("Invalid module or cheating attempt.");
        }

        List<QuizQuestion> questions = currentModule.getQuestions();
        Map<UUID, Integer> userAnswers = submission.getAnswers();

        if (userAnswers == null || userAnswers.size() != questions.size()) {
            throw new IllegalArgumentException("Number of answers does not match number of questions.");
        }

        int correctCount = 0;
        for (QuizQuestion question : questions) {
            Integer givenAnswerIndex = userAnswers.get(question.getId());
            if (givenAnswerIndex == null) {
                throw new IllegalArgumentException("Missing answer for question ID: " + question.getId());
            }
            if (givenAnswerIndex == question.getCorrectOptionIndex()) {
                correctCount++;
            }
        }

        double score = (double) correctCount / questions.size();
        status.setQuizScore(score);

        double requiredThreshold = training.getPassingThreshold() != null ? training.getPassingThreshold() : 0.8;

        if (score >= requiredThreshold) {
            advanceProgress(status);
            handleCourseCompletion(status);

            auditLogService.logEvent(status.getUser().getId(), "COURSE_COMPLETED", "User " + userEmail + " has completed course: " + training.getTitle() + ", scoring: " + (score * 100) + "%");
            return true;
        } else {
            status.setStatus(ProgressStatus.FAILED);
            userTrainingStatusRepository.save(status);

            auditLogService.logEvent(status.getUser().getId(), "QUIZ_FAILED",
                    "Quiz failed: " + training.getTitle() + ". Score: " + (score * 100) + "%");
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleDto getModule(UUID moduleId) {
        TrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        return mapModuleToDto(module);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionDto getQuestion(UUID questionId) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        return mapQuestionToDto(question);
    }

    @Override
    @Transactional
    public void unassignTrainingFromUser(UUID userId, UUID trainingId) {
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(userId, trainingId)
                .orElseThrow(() -> new RuntimeException("Training assignment not found for this user."));

        userTrainingStatusRepository.delete(status);

        auditLogService.logEvent(userId, "TRAINING_UNASSIGNED",
                "Removed assignment for training ID: " + trainingId);
    }

    @Override
    @Transactional
    public void revokeTrainingCompletion(UUID userId, UUID trainingId) {
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(userId, trainingId)
                .orElseThrow(() -> new RuntimeException("Training assignment not found."));

        if (status.getStatus() != ProgressStatus.COMPLETED) {
            throw new IllegalStateException("Cannot revoke completion because training is not completed yet.");
        }

        status.setStatus(ProgressStatus.NOT_STARTED);

        status.setCompletedAt(null);
        status.setValidUntil(null);
        status.setQuizScore(null);
        status.setPassRevoked(false);

        var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId)
                .orElse(null);
        status.setCurrentModule(firstModule);

        userTrainingStatusRepository.save(status);

        auditLogService.logEvent(userId, "TRAINING_COMPLETION_REVOKED",
                "Admin manually revoked completion for training ID: " + trainingId);
    }

    private UserTrainingStatus getUserTrainingStatus(String email, UUID trainingId) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return userTrainingStatusRepository.findByUserId(user.getId()).stream().filter(s -> s.getTraining().getId().equals(trainingId)).findFirst().orElseThrow(() -> new IllegalArgumentException("You are not assigned to this training."));
    }

    private void advanceProgress(UserTrainingStatus status) {
        if (status.getCurrentModule() == null) {
            finishTraining(status);
            return;
        }

        var nextModuleOptional = moduleRepository.findFirstByTrainingIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(
                status.getTraining().getId(),
                status.getCurrentModule().getOrderIndex()
        );

        if (nextModuleOptional.isPresent()) {
            status.setCurrentModule(nextModuleOptional.get());
            status.setStatus(ProgressStatus.IN_PROGRESS);
        } else {
            finishTraining(status);
        }

        userTrainingStatusRepository.save(status);
    }

    private void finishTraining(UserTrainingStatus status) {
        status.setStatus(ProgressStatus.COMPLETED);
        status.setCurrentModule(null);
        status.setCompletedAt(LocalDateTime.now());

        int validDays = status.getTraining().getValidityPeriodDays();
        status.setValidUntil(LocalDateTime.now().plusDays(validDays));
    }

    private Training mapToEntity(CreateTrainingRequest req) {
        Training training = Training.builder().title(req.getTitle()).description(req.getDescription()).type(req.getType()).validityPeriodDays(req.getValidityPeriodDays()).passingThreshold(req.getPassingThreshold() != null ? req.getPassingThreshold() : 0.8).modules(new java.util.ArrayList<>()).build();
        if (req.getModules() != null) {
            req.getModules().forEach(mReq -> {
                TrainingModule module = mapModuleToEntity(mReq);
                module.setTraining(training);
                module.setOrderIndex(training.getModules().size());
                training.getModules().add(module);
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
        return TrainingSummaryDto.builder().id(entity.getId()).title(entity.getTitle()).description(entity.getDescription()).type(entity.getType()).validityPeriodDays(entity.getValidityPeriodDays()).passingThreshold(entity.getPassingThreshold()).build();
    }

    private List<UserTrainingDto> fetchValidTrainingsInternal(UUID userId) {
        return userTrainingStatusRepository.findByUserId(userId).stream()
                .filter(status -> status.getStatus() == ProgressStatus.COMPLETED)
                .filter(status -> status.getValidUntil() != null && status.getValidUntil().isAfter(LocalDateTime.now()))
                .filter(status -> !status.isPassRevoked())
                .map(status -> UserTrainingDto.builder()
                        .id(status.getId())
                        .training(mapToSummaryDto(status.getTraining()))
                        .status(status.getStatus())
                        .completedAt(status.getCompletedAt())
                        .validUntil(status.getValidUntil())
                        .build())
                .collect(Collectors.toList());
    }

    private void handleCourseCompletion(UserTrainingStatus status) {
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            try {
                List<UserTrainingDto> validTrainings = fetchValidTrainingsInternal(status.getUser().getId());
                passService.sendPass(status.getUser(), validTrainings);
            } catch (Exception e) {
                auditLogService.logEvent(status.getUser().getId(), "PASS_AUTO_SEND_FAILED", "Error: " + e.getMessage());
            }
        }
    }

    private UserTrainingDto mapToUserTrainingDto(UserTrainingStatus status) {
        int calculatedIndex = 0;
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            if (status.getTraining().getModules() != null) {
                calculatedIndex = status.getTraining().getModules().size();
            }
        } else if (status.getCurrentModule() != null) {
            calculatedIndex = status.getCurrentModule().getOrderIndex();
        }

        return UserTrainingDto.builder()
                .id(status.getId())
                .training(mapToSummaryDto(status.getTraining()))
                .status(status.getStatus())
                .currentModuleIndex(calculatedIndex)
                .quizScore(status.getQuizScore())
                .completedAt(status.getCompletedAt())
                .validUntil(status.getValidUntil())
                .isPassRevoked(status.isPassRevoked())
                .build();
    }

    private void reindexModules(List<TrainingModule> modules) {
        for (int i = 0; i < modules.size(); i++) {
            modules.get(i).setOrderIndex(i);
        }
    }

    private void revokeProgressForModule(TrainingModule module) {
        UUID trainingId = module.getTraining().getId();
        List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(trainingId);
        int updatedCount = 0;

        for (UserTrainingStatus status : statuses) {

            boolean isCompleted = status.getStatus() == ProgressStatus.COMPLETED;
            boolean isAhead = status.getCurrentModule() != null
                    && status.getCurrentModule().getOrderIndex() > module.getOrderIndex();

            if (isCompleted || isAhead) {
                status.setStatus(ProgressStatus.IN_PROGRESS);
                status.setCurrentModule(module);

                status.setQuizScore(null);
                status.setCompletedAt(null);
                status.setValidUntil(null);

                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            userTrainingStatusRepository.saveAll(statuses);
            auditLogService.logEvent("TRAINING_PROGRESS_REVOKED",
                    "Reset progress for " + updatedCount + " users due to major update in module: " + module.getTitle());
        }
    }
}