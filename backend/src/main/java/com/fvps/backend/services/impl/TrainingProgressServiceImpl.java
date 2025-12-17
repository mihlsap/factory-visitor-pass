package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.*;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.events.UserStatusChangedEvent;
import com.fvps.backend.repositories.TrainingModuleRepository;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.PassService;
import com.fvps.backend.services.TrainingProgressService;
import com.fvps.backend.services.UserClearanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingProgressServiceImpl implements TrainingProgressService {

    private final UserTrainingStatusRepository userTrainingStatusRepository;
    private final UserRepository userRepository;
    private final TrainingRepository trainingRepository;
    private final TrainingModuleRepository moduleRepository;

    private final AuditLogService auditLogService;
    private final PassService passService;
    private final UserClearanceService clearanceService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.training.default-passing-threshold}")
    private double defaultPassingThreshold;

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
    @Transactional
    public void unassignTrainingFromUser(UUID userId, UUID trainingId) {
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(userId, trainingId)
                .orElseThrow(() -> new RuntimeException("Training assignment not found."));

        userTrainingStatusRepository.delete(status);
        auditLogService.logEvent(userId, "TRAINING_UNASSIGNED", "Removed assignment for training ID: " + trainingId);
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

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Sequential Logic:</b> Enforces strict order. The user cannot complete module B if the system thinks they are on module A.</li>
     * <li><b>Validation:</b> Rejects completion requests for {@link ModuleType#QUIZ}. Quizzes must be completed via {@link #submitQuiz}.</li>
     * <li><b>State Transition:</b> If this is the last module, triggers {@code handleCourseCompletion()}.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public void completeModule(String userEmail, UUID trainingId, UUID moduleId) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);

        if (status.getCurrentModule() == null && status.getStatus() == ProgressStatus.NOT_STARTED) {
            var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId).orElse(null);
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
            throw new IllegalArgumentException("Cannot skip modules! Current: " + status.getCurrentModule().getTitle());
        }

        if (status.getCurrentModule().getType() == ModuleType.QUIZ) {
            throw new IllegalArgumentException("Quiz must be solved, cannot be skipped.");
        }

        advanceProgress(status);
        handleCourseCompletion(status);

        auditLogService.logEvent(status.getUser().getId(), "MODULE_COMPLETED", "Completed module (Video/PDF).");
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Scoring:</b> Calculates the percentage of correct answers.</li>
     * <li><b>Pass/Fail:</b> Compares score to {@code training.passingThreshold}.
     * If >= threshold, advances progress. If < threshold, sets status to {@link ProgressStatus#FAILED}.</li>
     * <li><b>Completion:</b> If the quiz is passed, and it was the last module, this triggers certificate generation and clearance update.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public boolean submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);
        Training training = status.getTraining();

        if (status.getCurrentModule() == null && status.getStatus() == ProgressStatus.NOT_STARTED) {
            var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId).orElse(null);
            if (firstModule != null) {
                status.setCurrentModule(firstModule);
                userTrainingStatusRepository.save(status);
            }
        }

        double score = getScore(moduleId, submission, status);
        status.setQuizScore(score);

        double requiredThreshold = training.getPassingThreshold() != null ? training.getPassingThreshold() : defaultPassingThreshold;

        if (score >= requiredThreshold) {
            advanceProgress(status);
            handleCourseCompletion(status);
            auditLogService.logEvent(status.getUser().getId(), "COURSE_COMPLETED",
                    "User " + userEmail + " completed: " + training.getTitle() + ", score: " + (score * 100) + "%");
            return true;
        } else {
            status.setStatus(ProgressStatus.FAILED);
            userTrainingStatusRepository.save(status);
            auditLogService.logEvent(status.getUser().getId(), "QUIZ_FAILED",
                    "Quiz failed: " + training.getTitle() + ". Score: " + (score * 100) + "%");
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Side Effect:</b> Calls {@link UserClearanceService#recalculateUserClearance} because revoking a training might drop the user's security level.</li>
     * <li><b>Notification:</b> Publishes a {@link UserStatusChangedEvent} to notify the user via email.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public void revokeTrainingCompletion(UUID userId, UUID trainingId) {
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(userId, trainingId)
                .orElseThrow(() -> new RuntimeException("Training assignment not found."));

        if (status.getStatus() != ProgressStatus.COMPLETED) {
            throw new IllegalStateException("Training is not completed yet.");
        }

        status.setStatus(ProgressStatus.NOT_STARTED);
        status.setCompletedAt(null);
        status.setValidUntil(null);
        status.setQuizScore(null);
        status.setPassRevoked(false);

        var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId).orElse(null);
        status.setCurrentModule(firstModule);

        userTrainingStatusRepository.save(status);
        clearanceService.recalculateUserClearance(userId);

        eventPublisher.publishEvent(new UserStatusChangedEvent(this, status.getUser(),
                "Admin manually revoked completion: " + status.getTraining().getTitle()));

        auditLogService.logEvent(userId, "TRAINING_COMPLETION_REVOKED", "Admin revoked completion ID: " + trainingId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * Forces all completed users back to {@code IN_PROGRESS} at the start of the training.
     * This is used when content changes are critical. Triggers security clearance recalculation.
     * </p>
     */
    @Override
    @Transactional
    public void resetProgressForTraining(Training training) {
        TrainingModule firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(training.getId()).orElse(null);
        if (firstModule == null) return;

        List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(training.getId());
        int updatedCount = 0;

        for (UserTrainingStatus status : statuses) {
            if (status.getStatus() == ProgressStatus.COMPLETED) {
                status.setStatus(ProgressStatus.IN_PROGRESS);
                status.setCurrentModule(firstModule);
                status.setQuizScore(null);
                status.setCompletedAt(null);
                status.setValidUntil(null);
                status.setPassRevoked(false);

                updatedCount++;
                clearanceService.recalculateUserClearance(status.getUser().getId());

                eventPublisher.publishEvent(new UserStatusChangedEvent(this, status.getUser(),
                        "Major update in training: " + training.getTitle() + ". Re-completion required."));
            }
        }
        if (updatedCount > 0) {
            userTrainingStatusRepository.saveAll(statuses);
            auditLogService.logEvent("TRAINING_COMPLETION_RESET", "Reset for " + updatedCount + " users in: " + training.getTitle());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * Only resets users who have already passed the specific module
     * being updated (or are past it). Users who haven't reached this module yet are unaffected.
     * </p>
     */
    @Override
    @Transactional
    public void resetProgressForModule(TrainingModule module) {
        UUID trainingId = module.getTraining().getId();
        List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(trainingId);
        int updatedCount = 0;

        for (UserTrainingStatus status : statuses) {
            boolean isCompleted = status.getStatus() == ProgressStatus.COMPLETED;
            boolean isAhead = status.getCurrentModule() != null && status.getCurrentModule().getOrderIndex() > module.getOrderIndex();

            if (isCompleted || isAhead) {
                status.setStatus(ProgressStatus.IN_PROGRESS);
                status.setCurrentModule(module);
                status.setQuizScore(null);
                status.setCompletedAt(null);
                status.setValidUntil(null);
                updatedCount++;

                if (isCompleted) {
                    clearanceService.recalculateUserClearance(status.getUser().getId());
                }

                eventPublisher.publishEvent(new UserStatusChangedEvent(this, status.getUser(),
                        "Content update in " + module.getTraining().getTitle() + ". Retake module: " + module.getTitle()));
            }
        }
        if (updatedCount > 0) {
            userTrainingStatusRepository.saveAll(statuses);
            auditLogService.logEvent("TRAINING_PROGRESS_REVOKED", "Reset for " + updatedCount + " users due to module: " + module.getTitle());
        }
    }

    @Override
    @Transactional
    public void assignTrainingsByLevelToUser(UUID userId, int level) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Training> trainings = trainingRepository.findAllBySecurityLevel(level);

        if (trainings.isEmpty()) {
            throw new IllegalArgumentException("No trainings found for security level " + level);
        }

        int assignedCount = 0;

        for (Training training : trainings) {
            boolean alreadyAssigned = userTrainingStatusRepository.existsByUserIdAndTrainingId(userId, training.getId());

            if (!alreadyAssigned) {
                TrainingModule firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(training.getId())
                        .orElse(null);

                UserTrainingStatus status = UserTrainingStatus.builder()
                        .user(user)
                        .training(training)
                        .status(ProgressStatus.NOT_STARTED)
                        .currentModule(firstModule)
                        .build();

                userTrainingStatusRepository.save(status);
                assignedCount++;
            }
        }

        if (assignedCount > 0) {
            auditLogService.logEvent(userId, "BULK_TRAINING_ASSIGNED",
                    "Assigned " + assignedCount + " trainings for Security Level " + level);
        } else {
            auditLogService.logEvent(userId, "BULK_ASSIGNMENT_SKIPPED",
                    "User already has all trainings for Level " + level);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Resume Logic:</b> The {@code currentModuleId} is calculated as follows:
     * <ul>
     * <li>If {@code COMPLETED}: Points to the <b>first module</b> (allowing review).</li>
     * <li>If {@code NOT_STARTED}: Points to the <b>first module</b> (entry point).</li>
     * <li>If {@code IN_PROGRESS}: Points to the saved {@code currentModule} from a database.</li>
     * </ul>
     * </li>
     * <li><b>Security:</b> This implementation explicitly hides the {@code correctOptionIndex}
     * for all quiz questions to prevent users from inspecting the network traffic for answers.</li>
     * <li><b>Progress Flag:</b> Modules are marked as {@code completed} if their sequence order
     * is strictly lower than the user's current progress index.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public TrainingResponseDto getTrainingDetailsForUser(UUID trainingId, String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(user.getId(), trainingId)
                .orElseThrow(() -> new RuntimeException("Training not assigned or access denied"));

        Training training = status.getTraining();

        UUID currentModuleId = null;
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            if (!training.getModules().isEmpty()) {
                currentModuleId = training.getModules().getFirst().getId();
            }
        } else if (status.getCurrentModule() != null) {
            currentModuleId = status.getCurrentModule().getId();
        } else {
            var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId).orElse(null);
            if (firstModule != null) {
                currentModuleId = firstModule.getId();
            }
        }

        final int currentProgressIndex;

        if (status.getStatus() == ProgressStatus.COMPLETED) {
            currentProgressIndex = Integer.MAX_VALUE;
        } else if (status.getCurrentModule() != null) {
            currentProgressIndex = status.getCurrentModule().getOrderIndex();
        } else {
            currentProgressIndex = -1;
        }

        List<ModuleDto> modules = training.getModules().stream()
                .sorted(Comparator.comparingInt(TrainingModule::getOrderIndex))
                .map(module -> {
                    boolean isCompleted = module.getOrderIndex() < currentProgressIndex;

                    if (status.getStatus() == ProgressStatus.COMPLETED) {
                        isCompleted = true;
                    }

                    return ModuleDto.builder()
                            .id(module.getId())
                            .title(module.getTitle())
                            .type(module.getType())
                            .contentUrl(module.getContentUrl())
                            .orderIndex(module.getOrderIndex())
                            .completed(isCompleted)
                            .questions(module.getType() == ModuleType.QUIZ ? mapQuestionsForUser(module) : null)
                            .build();
                })
                .collect(Collectors.toList());

        return TrainingResponseDto.builder()
                .id(training.getId())
                .title(training.getTitle())
                .description(training.getDescription())
                .type(training.getType())
                .validityPeriodDays(training.getValidityPeriodDays())
                .passingThreshold(training.getPassingThreshold())
                .securityLevel(training.getSecurityLevel())
                .modules(modules)
                .currentModuleId(currentModuleId)
                .build();
    }

    private UserTrainingStatus getUserTrainingStatus(String email, UUID trainingId) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return userTrainingStatusRepository.findByUserId(user.getId()).stream()
                .filter(s -> s.getTraining().getId().equals(trainingId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("You are not assigned to this training."));
    }

    private void advanceProgress(UserTrainingStatus status) {
        if (status.getCurrentModule() == null) {
            finishTraining(status);
            return;
        }
        var nextModule = moduleRepository.findFirstByTrainingIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(
                status.getTraining().getId(), status.getCurrentModule().getOrderIndex());

        if (nextModule.isPresent()) {
            status.setCurrentModule(nextModule.get());
            status.setStatus(ProgressStatus.IN_PROGRESS);
        } else {
            finishTraining(status);
        }
        userTrainingStatusRepository.save(status);
    }

    private void finishTraining(UserTrainingStatus status) {
        status.setStatus(ProgressStatus.COMPLETED);
        status.setCurrentModule(null);
        status.setCompletedAt(LocalDateTime.now(clock));
        int validDays = status.getTraining().getValidityPeriodDays();
        status.setValidUntil(LocalDateTime.now(clock).plusDays(validDays));
    }

    private void handleCourseCompletion(UserTrainingStatus status) {
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            try {
                clearanceService.recalculateUserClearance(status.getUser().getId());
                passService.sendPassCompletionNotification(status.getUser());
            } catch (Exception e) {
                auditLogService.logEvent(status.getUser().getId(), "PASS_AUTO_SEND_FAILED", "Error: " + e.getMessage());
            }
        }
    }

    private List<UserTrainingDto> fetchValidTrainingsInternal(UUID userId) {
        return userTrainingStatusRepository.findByUserId(userId).stream()
                .filter(status -> status.getStatus() == ProgressStatus.COMPLETED)
                .filter(status -> status.getValidUntil() != null && status.getValidUntil().isAfter(LocalDateTime.now(clock)))
                .filter(status -> !status.isPassRevoked())
                .map(this::mapToUserTrainingDto)
                .collect(Collectors.toList());
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

    private double getScore(UUID moduleId, QuizSubmissionDto submission, UserTrainingStatus status) {
        List<QuizQuestion> questions = getQuizQuestions(moduleId, status);
        Map<UUID, Integer> userAnswers = submission.getAnswers();

        if (userAnswers == null || userAnswers.size() != questions.size()) {
            throw new IllegalArgumentException("Number of answers does not match number of questions.");
        }

        int correctCount = 0;
        for (QuizQuestion question : questions) {
            Integer givenAnswerIndex = userAnswers.get(question.getId());
            if (givenAnswerIndex == null)
                throw new IllegalArgumentException("Missing answer for Q: " + question.getId());
            if (givenAnswerIndex == question.getCorrectOptionIndex()) correctCount++;
        }

        return (double) correctCount / questions.size();
    }

    private List<QuizQuestion> getQuizQuestions(UUID moduleId, UserTrainingStatus status) {
        if (status.getCurrentModule() == null) {
            throw new IllegalArgumentException("Training already completed or no active module.");
        }

        if (!status.getCurrentModule().getId().equals(moduleId)) {
            throw new IllegalArgumentException("Attempt to solve quiz from another module.");
        }

        TrainingModule currentModule = status.getCurrentModule();
        if (currentModule.getType() != ModuleType.QUIZ) {
            throw new IllegalArgumentException("Invalid module type.");
        }

        return currentModule.getQuestions();
    }

    private List<QuestionDto> mapQuestionsForUser(TrainingModule module) {
        if (module.getQuestions() == null) return List.of();

        return module.getQuestions().stream()
                .map(q -> QuestionDto.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .options(q.getOptions())
                        .correctOptionIndex(null)
                        .build())
                .collect(Collectors.toList());
    }
}