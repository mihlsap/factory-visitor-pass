package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.QuizQuestion;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.TrainingModule;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.domain.enums.ResetMode;
import com.fvps.backend.domain.enums.TrainingType;
import com.fvps.backend.repositories.QuizQuestionRepository;
import com.fvps.backend.repositories.TrainingModuleRepository;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.TrainingContentService;
import com.fvps.backend.services.TrainingProgressService;
import jakarta.persistence.criteria.Predicate;
import com.fvps.backend.domain.enums.AppMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingContentServiceImpl implements TrainingContentService {

    private final TrainingRepository trainingRepository;
    private final TrainingModuleRepository moduleRepository;
    private final QuizQuestionRepository questionRepository;
    private final UserTrainingStatusRepository userTrainingStatusRepository;
    private final TrainingModuleRepository trainingModuleRepository;

    private final AuditLogService auditLogService;
    private final TrainingProgressService progressService;

    @Value("${app.training.default-passing-threshold}")
    private double defaultPassingThreshold;

    @Value("${app.training.default-security-level}")
    private int defaultSecurityLevel;

    @Override
    @Transactional
    public TrainingResponseDto createTraining(CreateTrainingRequest request) {
        if (request.getModules() != null) {
            request.getModules().forEach(this::validateModuleRequest);
        }

        Training training = mapToEntity(request);
        Training saved = trainingRepository.save(training);
        auditLogService.logEvent(AppMessage.TRAINING_CREATED.name(), "Training created: " + training.getTitle());
        return mapToDto(saved);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Optimistic Locking:</b> Verifies {@code request.version} against the
     * DB to prevent concurrent overwrites.</li>
     * <li><b>Validity Recalculation:</b> If the {@code validityPeriodDays} changes,
     * this method <b>automatically updates</b>
     * the expiration date (ValidUntil) for all users who currently hold a valid
     * pass for this training (unless the pass is revoked).</li>
     * <li><b>Reset Progress:</b> If {@code request.isResetProgress()} is true,
     * forces all users to retake the training.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public TrainingResponseDto updateTraining(UUID id, CreateTrainingRequest request) {
        Training training = getTrainingById(id);

        // Optimistic Locking check
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
        if (request.getPassingThreshold() != null)
            training.setPassingThreshold(request.getPassingThreshold());
        if (request.getSecurityLevel() > 0)
            training.setSecurityLevel(request.getSecurityLevel());

        training.setExcludedFromScore(request.isExcludedFromScore());

        Training saved = trainingRepository.save(training);

        // Logic for side effects (Reset vs Recalculate)
        if (request.isResetProgress()) {
            progressService.resetProgressForTraining(saved);
        } else if (validityChanged) {
            List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(id);
            int updatedCount = 0;
            for (UserTrainingStatus status : statuses) {
                if (status.getCompletedAt() != null) {
                    status.setValidUntil(status.getCompletedAt().plusDays(newValidityDays));
                    updatedCount++;
                }
            }
            if (updatedCount > 0) {
                userTrainingStatusRepository.saveAll(statuses);
                auditLogService.logEvent(AppMessage.TRAINING_VALIDITY_RECALCULATED.name(),
                        "Recalculated validity for " + updatedCount + " users.");
            }
        }

        auditLogService.logEvent(AppMessage.TRAINING_UPDATED.name(), "Training updated: " + training.getTitle());
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public void deleteTraining(UUID id) {
        Training training = getTrainingById(id);
        userTrainingStatusRepository.deleteByTrainingId(id);
        trainingRepository.delete(training);
        auditLogService.logEvent(AppMessage.TRAINING_DELETED.name(), "Deleted training: " + training.getTitle());
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
    public Page<TrainingSummaryDto> getAllTrainings(TrainingType type, Integer level, String search,
            Pageable pageable) {
        Specification<Training> spec = (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by Type
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            // Filter by Security Level
            if (level != null) {
                predicates.add(cb.equal(root.get("securityLevel"), level));
            }

            // Search (Title or ID)
            if (StringUtils.hasText(search)) {
                String likePattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), likePattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return trainingRepository.findAll(spec, pageable)
                .map(this::mapToSummaryDto);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li>Handles insertion at a specific index, shifting subsequent modules
     * automatically.</li>
     * <li>If this is the <b>first module</b> added to a training, it automatically
     * sets this module
     * as the {@code currentModule} for any users who have been assigned the
     * training but haven't started yet.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public TrainingResponseDto addModuleToTraining(UUID trainingId, CreateModuleRequest request) {
        validateModuleRequest(request);

        Training training = getTrainingById(trainingId);
        TrainingModule module = mapModuleToEntity(request);
        module.setTraining(training);

        if (training.getModules() == null)
            training.setModules(new java.util.ArrayList<>());
        List<TrainingModule> modules = training.getModules();

        if (request.getOrderIndex() != null && request.getOrderIndex() >= 0
                && request.getOrderIndex() < modules.size()) {
            modules.add(request.getOrderIndex(), module);
        } else {
            modules.add(module);
        }
        reindexModules(modules);
        Training saved = trainingRepository.save(training);

        int targetIndex = module.getOrderIndex();
        TrainingModule newModule = saved.getModules().stream().filter(m -> m.getOrderIndex() == targetIndex).findFirst()
                .orElse(saved.getModules().getLast());

        if (request.getResetMode() != null && request.getResetMode() != ResetMode.NONE) {
            progressService.resetProgressForModule(newModule, request.getResetMode());
        }

        // Edge case: Setup start point for existing assignments
        if (saved.getModules().size() == 1) {
            List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(trainingId);
            for (UserTrainingStatus s : statuses) {
                if (s.getStatus() == ProgressStatus.NOT_STARTED && s.getCurrentModule() == null) {
                    s.setCurrentModule(newModule);
                    userTrainingStatusRepository.save(s);
                }
            }
        }

        auditLogService.logEvent(AppMessage.MODULE_ADDED.name(), "Added module to: " + training.getTitle());
        return mapToDto(saved);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li>If the {@code type} changes (e.g. from QUIZ to INFORMATIONAL), all
     * associated questions are cleared.</li>
     * <li>Supports re-ordering modules by removing the module and re-inserting it
     * at the new {@code orderIndex}.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public TrainingResponseDto updateModule(UUID moduleId, UpdateModuleRequest request) {
        TrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));

        if (request.getVersion() != null && !request.getVersion().equals(module.getVersion())) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Module version mismatch.");
        }

        module.setTitle(request.getTitle());
        module.setContentUrl(request.getContentUrl());

        if (request.getPassingThreshold() != null) {
            module.setPassingThreshold(request.getPassingThreshold());
        }

        // Clear questions if the type changes away from QUIZ
        if (request.getType() != null) {
            module.setType(request.getType());
            if (request.getType() != ModuleType.QUIZ)
                module.getQuestions().clear();
        }

        Training training = module.getTraining();
        List<TrainingModule> modules = training.getModules();

        // Re-ordering logic
        if (request.getOrderIndex() != null && request.getOrderIndex() >= 0 && request.getOrderIndex() < modules.size()
                && request.getOrderIndex() != module.getOrderIndex()) {
            modules.remove(module);
            modules.add(request.getOrderIndex(), module);
            reindexModules(modules);
            trainingRepository.save(training);
        } else {
            moduleRepository.save(module);
        }

        if (request.getResetMode() != null && request.getResetMode() != ResetMode.NONE) {
            progressService.resetProgressForModule(module, request.getResetMode());
        }

        auditLogService.logEvent(AppMessage.MODULE_UPDATED.name(), "Module updated: " + module.getTitle());
        return mapToDto(module.getTraining());
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <b>Safety Check:</b> Throws {@link IllegalStateException} if any user is
     * currently active on this specific module.
     * This prevents corrupting the state of a user in the middle of a training
     * session.
     * </p>
     */
    @Override
    @Transactional
    public TrainingResponseDto deleteModule(UUID moduleId) {
        TrainingModule moduleToDelete = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        Training training = moduleToDelete.getTraining();

        boolean activeUsers = userTrainingStatusRepository.findAllByTrainingId(training.getId()).stream()
                .anyMatch(s -> s.getCurrentModule() != null && s.getCurrentModule().getId().equals(moduleId));

        if (activeUsers)
            throw new IllegalStateException("Cannot delete active module.");

        training.getModules().remove(moduleToDelete);
        moduleRepository.delete(moduleToDelete);
        reindexModules(training.getModules());
        trainingRepository.save(training);

        auditLogService.logEvent(AppMessage.MODULE_DELETED.name(), "Deleted module: " + moduleToDelete.getTitle());
        return mapToDto(training);
    }

    @Override
    @Transactional(readOnly = true)
    public ModuleDto getModule(UUID moduleId) {
        return mapModuleToDto(
                moduleRepository.findById(moduleId).orElseThrow(() -> new RuntimeException("Module not found")));
    }

    @Override
    @Transactional
    public TrainingResponseDto addQuestionToModule(UUID moduleId, CreateQuestionRequest request) {
        validateQuestionRequest(request.getOptions(), request.getCorrectOptionIndex());

        TrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        if (module.getType() != ModuleType.QUIZ)
            throw new IllegalStateException("Not a QUIZ module.");

        QuizQuestion question = mapQuestionToEntity(request);
        question.setModule(module);
        question.setOrderIndex(module.getQuestions() != null ? module.getQuestions().size() : 0);

        if (module.getQuestions() == null)
            module.setQuestions(new java.util.ArrayList<>());
        module.getQuestions().add(question);

        moduleRepository.save(module);

        if (request.getResetMode() != null && request.getResetMode() != ResetMode.NONE) {
            progressService.resetProgressForModule(module, request.getResetMode());
        }

        auditLogService.logEvent(AppMessage.QUESTION_ADDED.name(), "Added question to: " + module.getTitle());
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional
    public void reorderModules(UUID trainingId, List<UUID> moduleIds) {
        if (!trainingRepository.existsById(trainingId)) {
            throw new IllegalStateException("Training not found");
        }

        for (int i = 0; i < moduleIds.size(); i++) {
            UUID moduleId = moduleIds.get(i);
            TrainingModule module = trainingModuleRepository.findById(moduleId)
                    .orElseThrow(() -> new IllegalStateException("Module not found: " + moduleId));

            // Security check: ensure module actually belongs to this training
            if (!module.getTraining().getId().equals(trainingId)) {
                throw new IllegalArgumentException("Module " + moduleId + " does not belong to training " + trainingId);
            }

            module.setOrderIndex(i);
            trainingModuleRepository.save(module);
        }
    }

    @Override
    @Transactional
    public TrainingResponseDto updateQuestion(UUID questionId, UpdateQuestionRequest request) {
        QuizQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        if (request.getVersion() != null && !request.getVersion().equals(question.getVersion()))
            throw new org.springframework.dao.OptimisticLockingFailureException("Question version mismatch.");

        validateQuestionRequest(request.getOptions(), request.getCorrectOptionIndex());

        question.setQuestionText(request.getQuestionText());
        question.setOptions(request.getOptions());
        question.setCorrectOptionIndex(request.getCorrectOptionIndex());

        questionRepository.save(question);

        if (request.getResetMode() != null && request.getResetMode() != ResetMode.NONE) {
            progressService.resetProgressForModule(question.getModule(), request.getResetMode());
        }

        auditLogService.logEvent(AppMessage.QUESTION_UPDATED.name(), "Updated question ID: " + questionId);
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
        auditLogService.logEvent(AppMessage.QUESTION_DELETED.name(), "Deleted question ID: " + questionId);
        return mapToDto(module.getTraining());
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionDto getQuestion(UUID questionId) {
        return mapQuestionToDto(
                questionRepository.findById(questionId).orElseThrow(() -> new RuntimeException("Question not found")));
    }

    private Training mapToEntity(CreateTrainingRequest req) {
        Training training = Training.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .type(req.getType())
                .validityPeriodDays(req.getValidityPeriodDays())
                .passingThreshold(
                        req.getPassingThreshold() != null ? req.getPassingThreshold() : defaultPassingThreshold)
                .securityLevel(req.getSecurityLevel() > 0 ? req.getSecurityLevel() : defaultSecurityLevel)
                .excludedFromScore(req.isExcludedFromScore())
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
        TrainingModule module = TrainingModule.builder().title(req.getTitle()).type(req.getType())
                .contentUrl(req.getContentUrl())
                .passingThreshold(req.getPassingThreshold())
                .questions(new java.util.ArrayList<>()).build();
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
        return QuizQuestion.builder().questionText(req.getQuestionText()).options(req.getOptions())
                .correctOptionIndex(req.getCorrectOptionIndex()).build();
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
                .excludedFromScore(entity.isExcludedFromScore())
                .modules(entity.getModules() != null ? entity.getModules().stream().map(this::mapModuleToDto).toList()
                        : List.of())
                .build();
    }

    private ModuleDto mapModuleToDto(TrainingModule entity) {
        return ModuleDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .orderIndex(entity.getOrderIndex())
                .type(entity.getType())
                .contentUrl(entity.getContentUrl())
                .passingThreshold(entity.getPassingThreshold())
                .version(entity.getVersion())
                .questions(entity.getQuestions() != null
                        ? entity.getQuestions().stream().map(this::mapQuestionToDto).toList()
                        : List.of())
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
                .excludedFromScore(entity.isExcludedFromScore())
                .build();
    }

    private void reindexModules(List<TrainingModule> modules) {
        for (int i = 0; i < modules.size(); i++) {
            modules.get(i).setOrderIndex(i);
        }
    }

    private void validateQuestionRequest(List<String> options, Integer correctIndex) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Question must have at least one option.");
        }
        if (correctIndex == null) {
            throw new IllegalArgumentException("Correct option index is required.");
        }
        if (correctIndex < 0 || correctIndex >= options.size()) {
            throw new IllegalArgumentException(
                    String.format("Invalid correctOptionIndex: %d. Must be between 0 and %d.",
                            correctIndex, options.size() - 1));
        }
    }

    private void validateModuleRequest(CreateModuleRequest req) {
        if (req.getType() == ModuleType.QUIZ) {
            if (req.getQuestions() != null) {
                req.getQuestions().forEach(q -> validateQuestionRequest(q.getOptions(), q.getCorrectOptionIndex()));
            }
        }
    }
}