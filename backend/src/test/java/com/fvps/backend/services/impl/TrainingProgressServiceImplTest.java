package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
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
import com.fvps.backend.services.UserClearanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingProgressServiceImplTest {

    @Mock
    private UserTrainingStatusRepository userTrainingStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TrainingModuleRepository moduleRepository;
    @Mock
    private PassService passService;
    @Mock
    private UserClearanceService clearanceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private TrainingRepository trainingRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private TrainingProgressServiceImpl progressService;

    private final UUID userId = UUID.randomUUID();
    private final UUID trainingId = UUID.randomUUID();
    private final UUID moduleId = UUID.randomUUID();
    private final String userEmail = "test@example.com";

    private User user;
    private Training training;
    private TrainingModule quizModule;
    private UserTrainingStatus status;
    private QuizQuestion q1, q2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(progressService, "defaultPassingThreshold", 0.8);

        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        lenient().when(clock.instant()).thenReturn(Instant.parse("2025-01-01T12:00:00Z"));

        user = User.builder().id(userId).email(userEmail).build();

        training = Training.builder()
                .id(trainingId)
                .title("OHS Training")
                .passingThreshold(0.5)
                .validityPeriodDays(365)
                .modules(new ArrayList<>()) // Initialize modules list
                .build();

        q1 = QuizQuestion.builder().id(UUID.randomUUID()).correctOptionIndex(0).build();
        q2 = QuizQuestion.builder().id(UUID.randomUUID()).correctOptionIndex(1).build();

        quizModule = TrainingModule.builder()
                .id(moduleId)
                .type(ModuleType.QUIZ)
                .training(training)
                .questions(List.of(q1, q2))
                .orderIndex(0)
                .build();

        training.getModules().add(quizModule); // Link module to training

        status = UserTrainingStatus.builder()
                .user(user)
                .training(training)
                .currentModule(quizModule)
                .status(ProgressStatus.IN_PROGRESS)
                .build();
    }

    @Test
    void shouldPassQuiz_whenScoreIsAboveThreshold() {
        Map<UUID, Integer> answers = Map.of(
                q1.getId(), 0,
                q2.getId(), 1);
        QuizSubmissionDto submission = new QuizSubmissionDto(answers);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(status));

        // Mock that there are no further modules, so the training completes
        when(moduleRepository.findFirstByTrainingIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(
                eq(trainingId),
                eq(quizModule.getOrderIndex())))
                .thenReturn(Optional.empty());

        var result = progressService.submitQuiz(userEmail, trainingId, moduleId, submission);

        assertTrue(result.isPassed());
        assertEquals(ProgressStatus.COMPLETED, status.getStatus());
        assertNotNull(status.getCompletedAt());
        assertNotNull(status.getValidUntil());
        assertEquals(1.0, result.getScore());

        verify(userTrainingStatusRepository, atLeastOnce()).save(status);
        verify(clearanceService).recalculateUserClearance(userId);
        verify(passService).sendPassCompletionNotification(eq(user));
    }

    @Test
    void shouldFailQuiz_whenScoreIsBelowThreshold() {
        Map<UUID, Integer> answers = Map.of(
                q1.getId(), 1,
                q2.getId(), 0);
        QuizSubmissionDto submission = new QuizSubmissionDto(answers);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(status));

        var result = progressService.submitQuiz(userEmail, trainingId, moduleId, submission);

        assertFalse(result.isPassed());
        assertEquals(ProgressStatus.FAILED, status.getStatus());
        assertEquals(0.0, result.getScore());

        verify(userTrainingStatusRepository, atLeastOnce()).save(status);
        verify(clearanceService, never()).recalculateUserClearance(any());
    }

    @Test
    void shouldThrowException_whenSubmittingQuizForWrongModule() {
        UUID otherModuleId = UUID.randomUUID();

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(status));

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                progressService.submitQuiz(userEmail, trainingId, otherModuleId, new QuizSubmissionDto(Map.of()))
        );

        assertTrue(exception.getMessage().contains("Module not found"));
    }

    @Test
    void shouldResetProgress_whenAdminRequestsIt() {
        status.setStatus(ProgressStatus.COMPLETED);
        status.setCompletedAt(java.time.LocalDateTime.now(clock));
        status.setValidUntil(java.time.LocalDateTime.now(clock).plusDays(10));

        TrainingModule firstModule = quizModule;

        when(moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId))
                .thenReturn(Optional.of(firstModule));
        when(userTrainingStatusRepository.findAllByTrainingId(trainingId))
                .thenReturn(List.of(status));

        progressService.resetProgressForTraining(training);

        assertEquals(ProgressStatus.NOT_STARTED, status.getStatus());
        assertNull(status.getCompletedAt());
        assertNull(status.getValidUntil());
        assertEquals(firstModule, status.getCurrentModule());

        verify(eventPublisher).publishEvent(any(UserStatusChangedEvent.class));
        verify(userTrainingStatusRepository).saveAll(anyList());
    }

    @Test
    void shouldThrowException_whenAssigningAlreadyAssignedTraining() {
        when(userTrainingStatusRepository.existsByUserIdAndTrainingId(userId, trainingId)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> progressService.assignTrainingToUser(userId, trainingId));
        verify(userTrainingStatusRepository, never()).save(any());
    }

    @Test
    void shouldThrowException_whenSkippingModules() {
        TrainingModule module1 = TrainingModule.builder().id(UUID.randomUUID()).orderIndex(0).training(training)
                .build();
        TrainingModule module3 = TrainingModule.builder().id(UUID.randomUUID()).orderIndex(2).training(training)
                .build();

        status.setCurrentModule(module1);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(status));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> progressService.completeModule(userEmail, trainingId, module3.getId()));

        assertTrue(ex.getMessage().contains("Cannot skip modules"));
    }

    @Test
    void shouldAssignTraining_whenNotAlreadyAssigned() {
        when(userTrainingStatusRepository.existsByUserIdAndTrainingId(userId, trainingId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        when(moduleRepository.findFirstByTrainingIdOrderByOrderIndexAsc(trainingId))
                .thenReturn(Optional.of(quizModule));

        progressService.assignTrainingToUser(userId, trainingId);

        verify(userTrainingStatusRepository).save(any(UserTrainingStatus.class));
        verify(auditLogService).logEvent(eq(userId), eq("TRAINING_ASSIGNED"), anyString());
    }
}