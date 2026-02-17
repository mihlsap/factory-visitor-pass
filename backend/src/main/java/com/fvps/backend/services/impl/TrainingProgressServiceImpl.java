package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.dto.training.ModuleResultDto;
import com.fvps.backend.domain.entities.*;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.domain.enums.ResetMode;
import com.fvps.backend.domain.enums.TrainingType;
import com.fvps.backend.events.UserStatusChangedEvent;
import com.fvps.backend.repositories.TrainingModuleRepository;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.PassService;
import com.fvps.backend.services.TrainingProgressService;
import com.fvps.backend.services.UserClearanceService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import com.fvps.backend.domain.enums.AppMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        TrainingModule firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId)
                .orElse(null);

        UserTrainingStatus status = UserTrainingStatus.builder()
                .user(user)
                .training(training)
                .status(ProgressStatus.NOT_STARTED)
                .currentModule(firstModule)
                .build();

        userTrainingStatusRepository.save(status);
        auditLogService.logEvent(userId, AppMessage.TRAINING_ASSIGNED.name(),
                "Assigned training: " + training.getTitle());
    }

    @Override
    @Transactional
    public void unassignTrainingFromUser(UUID userId, UUID trainingId) {
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(userId, trainingId)
                .orElseThrow(() -> new RuntimeException("Training assignment not found."));

        userTrainingStatusRepository.delete(status);
        auditLogService.logEvent(userId, AppMessage.TRAINING_UNASSIGNED.name(),
                "Removed assignment for training ID: " + trainingId);
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
     * <li><b>Sequential Logic:</b> Enforces strict order. The user cannot complete
     * module B if the system thinks they are on module A.</li>
     * <li><b>Validation:</b> Rejects completion requests for
     * {@link ModuleType#QUIZ}. Quizzes must be completed via
     * {@link #submitQuiz}.</li>
     * <li><b>State Transition:</b> If this is the last module, triggers
     * {@code handleCourseCompletion()}.</li>
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
            if (status.getStatus() == ProgressStatus.COMPLETED)
                return;
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

        auditLogService.logEvent(status.getUser().getId(), AppMessage.MODULE_COMPLETED.name(),
                "Completed module (Video/PDF).");
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Scoring:</b> Calculates the percentage of correct answers.</li>
     * <li><b>Pass/Fail Logic:</b> The score is calculated and saved, but the
     * training status
     * is <b>NOT</b> immediately set to {@link ProgressStatus#FAILED} if the score
     * is below a threshold.
     * The user is allowed to proceed to subsequent modules.</li>
     * <li><b>Completion:</b> The final Pass/Fail status for the entire training is
     * determined
     * only when the user completes the last module (see
     * {@code finishTraining}).</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public QuizResultDto submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);
        Training training = status.getTraining();

        TrainingModule module = training.getModules().stream()
                .filter(m -> m.getId().equals(moduleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Module not found in this training"));

        // Validate module type
        if (module.getType() != ModuleType.QUIZ) {
            throw new IllegalArgumentException("Submitted module is not a quiz");
        }

        List<QuizQuestion> questions = module.getQuestions();
        Map<UUID, Integer> userAnswersMap = submission.getAnswers();

        int correctCount = 0;
        List<QuizQuestion> failedQuestions = new ArrayList<>();
        List<UserQuizAnswer> newAnswers = new ArrayList<>();

        for (QuizQuestion question : questions) {
            Integer givenAnswerIndex = userAnswersMap.get(question.getId());
            boolean isCorrect = false;

            if (givenAnswerIndex != null) {
                if (givenAnswerIndex == question.getCorrectOptionIndex()) {
                    correctCount++;
                    isCorrect = true;
                } else {
                    failedQuestions.add(question);
                }
            } else {
                failedQuestions.add(question);
            }

            // Save user answer
            UserQuizAnswer answer = UserQuizAnswer.builder()
                    .userTrainingStatus(status)
                    .trainingId(training.getId())
                    .moduleId(moduleId)
                    .questionId(question.getId())
                    .selectedOptionIndex(givenAnswerIndex != null ? givenAnswerIndex : -1)
                    .isCorrect(isCorrect)
                    .build();
            newAnswers.add(answer);
        }

        // Add new answers to the list (removing old ones for this module if any)
        status.getQuizAnswers().removeIf(a -> a.getModuleId().equals(moduleId));
        status.getQuizAnswers().addAll(newAnswers);

        double score = (double) correctCount / questions.size();
        double requiredThreshold = training.getPassingThreshold() != null ? training.getPassingThreshold()
                : defaultPassingThreshold;
        boolean passed = score >= requiredThreshold;

        // Advance progress ONLY if we are currently at this module
        // If we are retrying a past module, we don't move the pointer (it's already
        // ahead).
        // If we are retrying a future module (cheat?), we shouldn't allow it.
        boolean isCurrentModule = status.getCurrentModule() != null
                && status.getCurrentModule().getId().equals(moduleId);
        // Also handle a case where we might be at null (completed) but retrying.

        if (isCurrentModule) {
            advanceProgress(status);
        } else if (status.getStatus() == ProgressStatus.FAILED) {
            // If the user failed previously and is retrying, we must re-evaluate
            finishTraining(status);
        }

        // Always check completion in case this passed quiz helps?
        // Actually handleCourseCompletion logic checks all modules.
        handleCourseCompletion(status);

        userTrainingStatusRepository.save(status);

        QuizResultDto.QuizResultDtoBuilder resultBuilder = QuizResultDto.builder()
                .passed(passed)
                .score(score)
                .correctAnswersCount(correctCount)
                .totalQuestionsCount(questions.size())
                .passingThreshold(requiredThreshold);

        if (!failedQuestions.isEmpty()) {
            resultBuilder.incorrectQuestions(failedQuestions.stream()
                    .map(q -> QuestionDto.builder()
                            .id(q.getId())
                            .questionText(q.getQuestionText())
                            .options(q.getOptions())
                            .correctOptionIndex(q.getCorrectOptionIndex())
                            .build())
                    .collect(Collectors.toList()));
        }

        return resultBuilder.build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Side Effect:</b> Calls
     * {@link UserClearanceService#recalculateUserClearance} because resetting a
     * training might drop the user's security level.</li>
     * <li><b>Notification:</b> Publishes a {@link UserStatusChangedEvent} to notify
     * the user via email.</li>
     * <li><b>Scope:</b> Applies to <b>any</b> status. Forces a clean restart.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional
    public void resetUserTrainingProgress(UUID userId, UUID trainingId) {
        UserTrainingStatus status = userTrainingStatusRepository.findByUserIdAndTrainingId(userId, trainingId)
                .orElseThrow(() -> new RuntimeException("Training assignment not found."));

        status.setStatus(ProgressStatus.NOT_STARTED);
        status.setCompletedAt(null);
        status.setValidUntil(null);
        status.getQuizAnswers().clear();

        var firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId).orElse(null);
        status.setCurrentModule(firstModule);

        userTrainingStatusRepository.save(status);
        clearanceService.recalculateUserClearance(userId);

        eventPublisher.publishEvent(new UserStatusChangedEvent(this, status.getUser(),
                "Admin manually reset progress for: " + status.getTraining().getTitle()));

        auditLogService.logEvent(userId, AppMessage.TRAINING_PROGRESS_RESET.name(),
                "Admin reset progress for training ID: " + trainingId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * Forces <b>ALL</b> assigned users (regardless of their current status) back to
     * {@link ProgressStatus#NOT_STARTED} at the beginning of the training.
     * <p>
     * Actions performed:
     * <ul>
     * <li>Resets status to {@code NOT_STARTED}.</li>
     * <li>Sets the current module to the first module of the training.</li>
     * <li>Clears {@code completedAt} and {@code validUntil} timestamps.</li>
     * <li><b>Clears all quiz answers</b> to ensure a clean slate.</li>
     * <li>Triggers security clearance recalculation for each user.</li>
     * </ul>
     * This is used when content changes are critical and require a full retake.
     * </p>
     */
    @Override
    @Transactional
    public void resetProgressForTraining(Training training) {
        TrainingModule firstModule = moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(training.getId())
                .orElse(null);
        if (firstModule == null)
            return;

        List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(training.getId());
        int updatedCount = 0;

        for (UserTrainingStatus status : statuses) {
            status.setStatus(ProgressStatus.NOT_STARTED);
            status.setCurrentModule(firstModule);
            status.setCompletedAt(null);
            status.setValidUntil(null);
            status.getQuizAnswers().clear();

            updatedCount++;
            clearanceService.recalculateUserClearance(status.getUser().getId());

            eventPublisher.publishEvent(new UserStatusChangedEvent(this, status.getUser(),
                    "Major update in training: " + training.getTitle() + ". Re-completion required."));
        }
        if (updatedCount > 0) {
            userTrainingStatusRepository.saveAll(statuses);
            auditLogService.logEvent(AppMessage.TRAINING_PROGRESS_RESET.name(),
                    "Reset for " + updatedCount + " users in: " + training.getTitle());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * Resets users based on the specified {@link ResetMode}:
     * <ul>
     * <li>{@code CURRENT_ONLY}: Resets progress for this module. Clears answers for
     * this module.</li>
     * <li>{@code CASCADE}: Resets progress for this module AND all subsequent
     * modules. Clears answers for all affected modules.</li>
     * </ul>
     * Users who haven't reached this module yet are unaffected.
     * </p>
     */
    @Override
    @Transactional
    public void resetProgressForModule(TrainingModule module, ResetMode mode) {
        if (mode == null || mode == ResetMode.NONE) {
            return;
        }

        UUID trainingId = module.getTraining().getId();
        List<UserTrainingStatus> statuses = userTrainingStatusRepository.findAllByTrainingId(trainingId);
        int updatedCount = 0;

        for (UserTrainingStatus status : statuses) {
            boolean isCompleted = status.getStatus() == ProgressStatus.COMPLETED;
            boolean isAhead = status.getCurrentModule() != null
                    && status.getCurrentModule().getOrderIndex() > module.getOrderIndex();

            // We apply reset if the user has completed the training OR is past this module
            // OR is currently ON this module (re-take current)
            boolean isOnModule = status.getCurrentModule() != null
                    && status.getCurrentModule().getId().equals(module.getId());

            if (isCompleted || isAhead || isOnModule) {
                // Common reset logic: Move a pointer back to this module
                if (isCompleted || isAhead) {
                    status.setStatus(ProgressStatus.IN_PROGRESS);
                    status.setCurrentModule(module);
                    status.setCompletedAt(null);
                    status.setValidUntil(null);

                    if (isCompleted) {
                        clearanceService.recalculateUserClearance(status.getUser().getId());
                    }
                }

                // Data clearing logic based on mode
                if (mode == ResetMode.CURRENT_ONLY) {
                    status.getQuizAnswers().removeIf(a -> a.getModuleId().equals(module.getId()));
                } else if (mode == ResetMode.CASCADE) {
                    // Remove answers for this module AND any module with a higher orderIndex
                    status.getQuizAnswers().removeIf(a -> {
                        boolean isThisModule = a.getModuleId().equals(module.getId());
                        // We need to check the order of the module the answer belongs to.
                        // But the answer only has moduleId. Logic requires fetching modules or assumes
                        // safety.
                        // Optimization: Since we are in CASCADE, we can assume we want to clear future
                        // answers.
                        // However, 'getQuizAnswers' is a list.
                        // To do this strictly correctly without N+1 queries for every answer's module:
                        // We can rely on the fact that we are resetting from 'module'.
                        // We should find the specific module IDs that are >= current module.
                        return isThisModule || isAnswerForFutureModule(a, module.getOrderIndex(), module.getTraining());
                    });
                }

                updatedCount++;

                eventPublisher.publishEvent(new UserStatusChangedEvent(this, status.getUser(),
                        "Content update in " + module.getTraining().getTitle() + ". Retake module: "
                                + module.getTitle()));
            }
        }
        if (updatedCount > 0) {
            userTrainingStatusRepository.saveAll(statuses);
            auditLogService.logEvent(AppMessage.TRAINING_PROGRESS_RESET.name(),
                    "Reset (" + mode + ") for " + updatedCount + " users due to module: " + module.getTitle());
        }
    }

    private boolean isAnswerForFutureModule(UserQuizAnswer answer, int thresholdIndex, Training training) {
        // Find a module for this answer from the training object (which should be loaded
        // in context ideally)
        // 'training' passed here is from the module object, so it might be a proxy or
        // full object.
        return training.getModules().stream()
                .filter(m -> m.getId().equals(answer.getModuleId()))
                .findFirst()
                .map(m -> m.getOrderIndex() > thresholdIndex)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingAssignmentDto> getTrainingAssignments(UUID trainingId) {
        return userTrainingStatusRepository.findAllByTrainingId(trainingId).stream()
                .map(status -> TrainingAssignmentDto.builder()
                        .userId(status.getUser().getId())
                        .name(status.getUser().getName())
                        .surname(status.getUser().getSurname())
                        .email(status.getUser().getEmail())
                        .status(status.getStatus())
                        .completedAt(status.getCompletedAt())
                        .build())
                .collect(Collectors.toList());
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
            boolean alreadyAssigned = userTrainingStatusRepository.existsByUserIdAndTrainingId(userId,
                    training.getId());

            if (!alreadyAssigned) {
                TrainingModule firstModule = moduleRepository
                        .findFirstByTrainingIdOrderByOrderIndexAsc(training.getId())
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
            auditLogService.logEvent(userId, AppMessage.TRAININGS_ASSIGNED_BULK.name(),
                    "Assigned " + assignedCount + " trainings for Security Level " + level);
        } else {
            auditLogService.logEvent(userId, AppMessage.BULK_ASSIGNMENT_SKIPPED.name(),
                    "User already has all trainings for Level " + level);
        }
    }

    @Override
    @Transactional
    public void assignTrainingToLevel(UUID trainingId, Integer securityLevel) {
        // 1. Identify "Required" trainings for this level (excluding the current one and
        // optional ones)
        List<Training> allLevelTrainings = trainingRepository.findAllBySecurityLevel(securityLevel);

        List<UUID> requiredTrainingIds = allLevelTrainings.stream()
                .filter(t -> !t.getId().equals(trainingId))
                .filter(t -> !t.isExcludedFromScore())
                .map(Training::getId)
                .collect(Collectors.toList());

        List<User> eligibleUsers;

        if (requiredTrainingIds.isEmpty()) {
            // Case A: No other required trainings exist for this level.
            // Eligibility falls back to the *previous* level.
            // If Level 1, everyone (Level 0+) is eligible.
            if (securityLevel <= 1) {
                eligibleUsers = userRepository.findAll();
            } else {
                eligibleUsers = userRepository.findByClearanceLevelGreaterThanEqual(securityLevel - 1);
            }
        } else {
            // Case B: Other required trainings exist.
            // Find users who have AT LEAST ONE of the other required trainings assigned.
            // This captures everyone "active" at this level (including those who completed
            // them).
            List<UUID> eligibleUserIds = userTrainingStatusRepository
                    .findUserIdsByAssignedTrainingIds(requiredTrainingIds);
            eligibleUsers = userRepository.findAllById(eligibleUserIds);
        }

        for (User user : eligibleUsers) {
            // Check to avoid duplicates
            if (!userTrainingStatusRepository.existsByUserIdAndTrainingId(user.getId(), trainingId)) {
                assignTrainingToUser(user.getId(), trainingId);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Resume Logic:</b> The {@code currentModuleId} is calculated as
     * follows:
     * <ul>
     * <li>If {@code COMPLETED}: Points to the <b>first module</b> (allowing
     * review).</li>
     * <li>If {@code NOT_STARTED}: Points to the <b>first module</b> (entry
     * point).</li>
     * <li>If {@code IN_PROGRESS}: Points to the saved {@code currentModule} from a
     * database.</li>
     * </ul>
     * </li>
     * <li><b>Security:</b> This implementation explicitly hides the
     * {@code correctOptionIndex}
     * for all quiz questions to prevent users from inspecting the network traffic
     * for answers.</li>
     * <li><b>Progress Flag:</b> Modules are marked as {@code completed} if their
     * sequence order
     * is strictly lower than the user's current progress index.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public UserTrainingDetailsDto getTrainingDetailsForUser(UUID trainingId, String userEmail) {
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

        int totalModules = training.getModules().size();

        // Clamp the existing index for display if needed, but for logic we use orderIndex
        int displayModuleIndex = (status.getStatus() == ProgressStatus.COMPLETED) ? totalModules
                : (Math.max(currentProgressIndex, 0));

        List<ModuleDto> modules = training.getModules().stream()
                .sorted(Comparator.comparingInt(TrainingModule::getOrderIndex))
                .map(module -> {
                    boolean isCompleted = module.getOrderIndex() < currentProgressIndex;

                    if (status.getStatus() == ProgressStatus.COMPLETED || status.getStatus() == ProgressStatus.FAILED) {
                        isCompleted = true;
                    }

                    boolean isPassed;
                    if (module.getType() == ModuleType.QUIZ) {
                        long totalQuestions = module.getQuestions().size();
                        long correctAnswers = 0;
                        boolean hasAnswers = false;
                        if (status.getQuizAnswers() != null) {
                            hasAnswers = status.getQuizAnswers().stream()
                                    .anyMatch(a -> a.getModuleId().equals(module.getId()));
                            correctAnswers = status.getQuizAnswers().stream()
                                    .filter(a -> a.getModuleId().equals(module.getId()) && a.isCorrect())
                                    .count();
                        }
                        double score = totalQuestions > 0 ? (double) correctAnswers / totalQuestions : 0.0;
                        double threshold = module.getPassingThreshold() != null ? module.getPassingThreshold()
                                : (training.getPassingThreshold() != null ? training.getPassingThreshold()
                                        : defaultPassingThreshold);

                        if (status.getStatus() == ProgressStatus.COMPLETED) {
                            isPassed = true;
                        } else {
                            isPassed = hasAnswers && score >= threshold;
                        }
                    } else {
                        // Non-quiz modules are "passed" if they are completed
                        isPassed = isCompleted;
                    }

                    return ModuleDto.builder()
                            .id(module.getId())
                            .title(module.getTitle())
                            .type(module.getType())
                            .contentUrl(module.getContentUrl())
                            .orderIndex(module.getOrderIndex())
                            .completed(isCompleted)
                            .passed(isPassed)
                            .questions(module.getType() == ModuleType.QUIZ ? mapQuestionsForUser(module) : null)
                            .build();
                })
                .collect(Collectors.toList());

        int progressPercentage = 0;
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            progressPercentage = 100;
        } else {
            long passedCount = modules.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getPassed()))
                    .count();
            if (totalModules > 0) {
                progressPercentage = (int) ((double) passedCount / totalModules * 100);
            }
        }

        return UserTrainingDetailsDto.builder()
                .id(training.getId())
                .title(training.getTitle())
                .description(training.getDescription())
                .type(training.getType())
                .status(status.getStatus())
                .validityPeriodDays(training.getValidityPeriodDays())
                .passingThreshold(training.getPassingThreshold())
                .securityLevel(training.getSecurityLevel())
                .modules(modules)
                .currentModuleId(currentModuleId)
                .progressPercentage(progressPercentage)
                .currentModuleIndex(displayModuleIndex)
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * This implementation leverages a custom JPQL query in
     * {@code UserTrainingStatusRepository}
     * to handle nullable filter parameters dynamically (i.e.,
     * {@code :param IS NULL OR field = :param}).
     * This ensures that if a filter is not provided by the API, it is ignored at
     * the database level.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public Page<UserTrainingDto> getUserTrainings(
            String userEmail,
            Pageable pageable,
            TrainingType type,
            Integer securityLevel,
            ProgressStatus status,
            String search) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Specification<UserTrainingStatus> spec = (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // User filter
            predicates.add(cb.equal(root.get("user").get("id"), user.getId()));

            // Joins
            Join<UserTrainingStatus, Training> trainingJoin = root.join("training");

            // Type filter
            if (type != null) {
                predicates.add(cb.equal(trainingJoin.get("type"), type));
            }

            // Level filter
            if (securityLevel != null) {
                predicates.add(cb.equal(trainingJoin.get("securityLevel"), securityLevel));
            }

            // Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Search filter
            if (search != null && !search.isBlank()) {
                String likePattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(trainingJoin.get("title")), likePattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userTrainingStatusRepository.findAll(spec, pageable)
                .map(this::mapToUserTrainingDto);
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
        Training training = status.getTraining();
        List<TrainingModule> quizModules = training.getModules().stream()
                .filter(m -> m.getType() == ModuleType.QUIZ)
                .toList();

        boolean allQuizzesPassed = true;

        // Default training threshold if not set
        double trainingThreshold = training.getPassingThreshold() != null ? training.getPassingThreshold()
                : defaultPassingThreshold;

        for (TrainingModule quiz : quizModules) {
            long totalQuestions = quiz.getQuestions().size();
            long correctAnswers = status.getQuizAnswers().stream()
                    .filter(a -> a.getModuleId().equals(quiz.getId()) && a.isCorrect())
                    .count();

            double score = totalQuestions > 0 ? (double) correctAnswers / totalQuestions : 0.0;

            // Use module-specific threshold if available, otherwise global
            double threshold = quiz.getPassingThreshold() != null ? quiz.getPassingThreshold() : trainingThreshold;

            if (score < threshold) {
                allQuizzesPassed = false;
                break;
            }
        }

        if (allQuizzesPassed) {
            status.setStatus(ProgressStatus.COMPLETED);
            status.setCompletedAt(LocalDateTime.now(clock));
            int validDays = training.getValidityPeriodDays();
            status.setValidUntil(LocalDateTime.now(clock).plusDays(validDays));
        } else {
            status.setStatus(ProgressStatus.FAILED);
            // Do not set completedAt or validUntil
        }

        status.setCurrentModule(null);
    }

    private void handleCourseCompletion(UserTrainingStatus status) {
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            try {
                clearanceService.recalculateUserClearance(status.getUser().getId());
                passService.sendPassCompletionNotification(status.getUser());
            } catch (Exception e) {
                auditLogService.logEvent(status.getUser().getId(), AppMessage.PASS_AUTO_SEND_FAILED.name(),
                        "Error: " + e.getMessage());
            }
        }
    }

    private List<UserTrainingDto> fetchValidTrainingsInternal(UUID userId) {
        return userTrainingStatusRepository.findByUserId(userId).stream()
                .filter(status -> status.getStatus() == ProgressStatus.COMPLETED)
                .filter(status -> status.getValidUntil() != null
                        && status.getValidUntil().isAfter(LocalDateTime.now(clock)))
                .filter(status -> status.getValidUntil() != null
                        && status.getValidUntil().isAfter(LocalDateTime.now(clock)))
                .map(this::mapToUserTrainingDto)
                .collect(Collectors.toList());
    }

    private UserTrainingDto mapToUserTrainingDto(UserTrainingStatus status) {
        int totalModules = 0;
        List<TrainingModule> modules = status.getTraining().getModules();
        if (modules != null) {
            totalModules = modules.size();
        }

        int currentModuleIndex = 0;
        if (status.getStatus() == ProgressStatus.COMPLETED || status.getStatus() == ProgressStatus.FAILED) {
            currentModuleIndex = totalModules;
        } else if (status.getCurrentModule() != null) {
            currentModuleIndex = status.getCurrentModule().getOrderIndex();
        }

        int progressPercentage = 0;
        if (status.getStatus() == ProgressStatus.COMPLETED) {
            progressPercentage = 100;
        } else if (status.getStatus() == ProgressStatus.FAILED && totalModules > 0) {

            long passedModulesCount = 0;
            double trainingThreshold = status.getTraining().getPassingThreshold() != null
                    ? status.getTraining().getPassingThreshold()
                    : defaultPassingThreshold;

            for (TrainingModule m : modules) {
                if (m.getType() == ModuleType.QUIZ) {
                    long totalQuestions = m.getQuestions().size();
                    long correctAnswers = 0;
                    if (status.getQuizAnswers() != null) {
                        correctAnswers = status.getQuizAnswers().stream()
                                .filter(a -> a.getModuleId().equals(m.getId()) && a.isCorrect())
                                .count();
                    }
                    double score = totalQuestions > 0 ? (double) correctAnswers / totalQuestions : 0.0;
                    double threshold = m.getPassingThreshold() != null ? m.getPassingThreshold()
                            : trainingThreshold;

                    if (score >= threshold) {
                        passedModulesCount++;
                    }
                } else {
                    // Non-quiz modules are considered passed if status is FAILED (since we finished
                    // the flow)
                    passedModulesCount++;
                }
            }
            progressPercentage = (int) ((double) passedModulesCount / totalModules * 100);

        } else if (totalModules > 0) {
            progressPercentage = (int) ((double) currentModuleIndex / totalModules * 100);
        }

        return UserTrainingDto.builder()
                .id(status.getId())
                .training(mapToSummaryDto(status.getTraining()))
                .status(status.getStatus())
                .currentModuleIndex(currentModuleIndex)
                .totalModules(totalModules)
                .progressPercentage(progressPercentage)
                .completedAt(status.getCompletedAt())
                .validUntil(status.getValidUntil())
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

    @Override
    @Transactional(readOnly = true)
    public TrainingSummaryDto getTrainingSummary(UUID trainingId, String userEmail) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);
        Training training = status.getTraining();

        List<ModuleResultDto> results = new ArrayList<>();

        List<TrainingModule> quizModules = training.getModules().stream()
                .filter(m -> m.getType() == ModuleType.QUIZ)
                .toList();

        double threshold = training.getPassingThreshold() != null ? training.getPassingThreshold()
                : defaultPassingThreshold;
        boolean overallPassed = true;

        for (TrainingModule quiz : quizModules) {
            long totalQuestions = quiz.getQuestions().size();
            List<UserQuizAnswer> allAnswers = status.getQuizAnswers();
            long correctAnswers = 0;
            if (allAnswers != null) {
                correctAnswers = allAnswers.stream()
                        .filter(a -> a.getModuleId().equals(quiz.getId()) && a.isCorrect())
                        .count();
            }

            double moduleThreshold = quiz.getPassingThreshold() != null ? quiz.getPassingThreshold() : threshold;
            double score = totalQuestions > 0 ? (double) correctAnswers / totalQuestions : 0.0;
            boolean passed = score >= moduleThreshold;
            if (!passed)
                overallPassed = false;

            results.add(ModuleResultDto.builder()
                    .moduleId(quiz.getId())
                    .moduleTitle(quiz.getTitle())
                    .score(score)
                    .passingThreshold(moduleThreshold)
                    .passed(passed)
                    .correctAnswers((int) correctAnswers)
                    .totalQuestions((int) totalQuestions)
                    .build());
        }

        return TrainingSummaryDto.builder()
                .id(training.getId())
                .title(training.getTitle())
                .description(training.getDescription())
                .type(training.getType())
                .validityPeriodDays(training.getValidityPeriodDays())
                .passingThreshold(threshold)
                .securityLevel(training.getSecurityLevel())
                .passed(overallPassed)
                .completedAt(status.getCompletedAt())
                .quizResults(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserQuizAnswerDto> getQuizReview(UUID trainingId, UUID moduleId, String userEmail) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);

        java.util.Map<UUID, Integer> correctAnswersMap = status.getTraining().getModules().stream()
                .filter(m -> m.getId().equals(moduleId))
                .findFirst()
                .map(m -> m.getQuestions().stream()
                        .collect(Collectors.toMap(QuizQuestion::getId, QuizQuestion::getCorrectOptionIndex)))
                .orElse(java.util.Collections.emptyMap());

        return status.getQuizAnswers().stream()
                .filter(a -> a.getModuleId().equals(moduleId))
                .map(a -> UserQuizAnswerDto.builder()
                        .questionId(a.getQuestionId())
                        .selectedOptionIndex(a.getSelectedOptionIndex())
                        .isCorrect(a.isCorrect())
                        .correctOptionIndex(correctAnswersMap.get(a.getQuestionId()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<QuestionDto> mapQuestionsForUser(TrainingModule module) {
        if (module.getQuestions() == null)
            return List.of();

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