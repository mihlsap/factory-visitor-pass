package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserClearanceServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserTrainingStatusRepository userTrainingStatusRepository;

    @Mock
    private TrainingRepository trainingRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private Clock clock;

    @InjectMocks
    private UserClearanceServiceImpl clearanceService;

    private final UUID userId = UUID.randomUUID();
    private final UUID trainingId1 = UUID.randomUUID();
    private final UUID trainingId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        lenient().when(clock.instant()).thenReturn(Instant.parse("2025-01-01T12:00:00Z"));
    }

    @Test
    void shouldThrowException_whenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> clearanceService.recalculateUserClearance(userId));
    }

    @Test
    void shouldGrantLevel1_whenUserCompletedRequiredTraining() {
        User user = User.builder().id(userId).clearanceLevel(0).build();
        Training trainingLvl1 = Training.builder().id(trainingId1).securityLevel(1).build();

        UserTrainingStatus status = UserTrainingStatus.builder()
                .training(trainingLvl1)
                .status(ProgressStatus.COMPLETED)
                .validUntil(LocalDateTime.now(clock).plusDays(10))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(status));

        when(trainingRepository.findAllBySecurityLevel(1)).thenReturn(List.of(trainingLvl1));
        when(trainingRepository.findAllBySecurityLevel(gt(1))).thenReturn(Collections.emptyList());

        clearanceService.recalculateUserClearance(userId);

        assertEquals(1, user.getClearanceLevel());

        verify(userRepository).save(user);
        verify(auditLogService).logEvent(eq(userId), eq("CLEARANCE_CHANGED"), anyString());
    }

    @Test
    void shouldStopAtLevel0_whenTrainingIsExpired() {
        User user = User.builder().id(userId).clearanceLevel(1).build();
        Training trainingLvl1 = Training.builder().id(trainingId1).securityLevel(1).build();

        UserTrainingStatus status = UserTrainingStatus.builder()
                .training(trainingLvl1)
                .status(ProgressStatus.COMPLETED)
                .validUntil(LocalDateTime.now(clock).minusDays(1))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(status));
        when(trainingRepository.findAllBySecurityLevel(1)).thenReturn(List.of(trainingLvl1));

        clearanceService.recalculateUserClearance(userId);

        assertEquals(0, user.getClearanceLevel());
        verify(userRepository).save(user);
    }

    @Test
    void shouldNotSaveUser_whenClearanceLevelDidNotChange() {
        User user = User.builder().id(userId).clearanceLevel(0).build();
        Training trainingLvl1 = Training.builder().id(trainingId1).securityLevel(1).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(trainingRepository.findAllBySecurityLevel(1)).thenReturn(List.of(trainingLvl1));

        clearanceService.recalculateUserClearance(userId);

        assertEquals(0, user.getClearanceLevel());
        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).logEvent(any(), any(), any());
    }

    @Test
    void shouldHandleCascadingLevelsCorrectly() {
        User user = User.builder().id(userId).clearanceLevel(0).build();

        Training trainingA = Training.builder().id(trainingId1).securityLevel(1).build();
        Training trainingB = Training.builder().id(trainingId2).securityLevel(2).build();
        Training trainingC = Training.builder().id(UUID.randomUUID()).securityLevel(3).build();

        UserTrainingStatus statusA = UserTrainingStatus.builder()
                .training(trainingA).status(ProgressStatus.COMPLETED)
                .validUntil(LocalDateTime.now(clock).plusDays(5)).build();

        UserTrainingStatus statusC = UserTrainingStatus.builder()
                .training(trainingC).status(ProgressStatus.COMPLETED)
                .validUntil(LocalDateTime.now(clock).plusDays(5)).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userTrainingStatusRepository.findByUserId(userId)).thenReturn(List.of(statusA, statusC));

        when(trainingRepository.findAllBySecurityLevel(1)).thenReturn(List.of(trainingA));
        when(trainingRepository.findAllBySecurityLevel(2)).thenReturn(List.of(trainingB));

        clearanceService.recalculateUserClearance(userId);

        assertEquals(1, user.getClearanceLevel());
        verify(userRepository).save(user);
    }
}