package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.CreateTrainingRequest;
import com.fvps.backend.domain.dto.training.TrainingResponseDto;
import com.fvps.backend.domain.dto.training.UpdateModuleRequest;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.TrainingModule;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.TrainingType;
import com.fvps.backend.repositories.TrainingModuleRepository;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.TrainingProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingContentServiceImplTest {

    @Mock private TrainingRepository trainingRepository;
    @Mock private TrainingModuleRepository moduleRepository;
    @Mock private UserTrainingStatusRepository userTrainingStatusRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private TrainingProgressService progressService;

    @InjectMocks
    private TrainingContentServiceImpl contentService;

    private final UUID trainingId = UUID.randomUUID();
    private final UUID moduleId = UUID.randomUUID();
    private Training training;
    private TrainingModule module;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contentService, "defaultPassingThreshold", 0.8);
        ReflectionTestUtils.setField(contentService, "defaultSecurityLevel", 1);

        training = Training.builder()
                .id(trainingId)
                .title("Original Title")
                .version(1L)
                .validityPeriodDays(365)
                .modules(new ArrayList<>())
                .build();

        module = TrainingModule.builder()
                .id(moduleId)
                .title("Module 1")
                .type(ModuleType.VIDEO)
                .training(training)
                .version(1L)
                .build();

        training.getModules().add(module);
    }

    @Test
    void shouldCreateTraining_withDefaults() {
        CreateTrainingRequest request = new CreateTrainingRequest();
        request.setTitle("New Training");
        request.setDescription("Desc");
        request.setType(TrainingType.OHS);
        request.setValidityPeriodDays(30);

        when(trainingRepository.save(any(Training.class))).thenAnswer(i -> {
            Training t = i.getArgument(0);
            t.setId(trainingId);
            return t;
        });

        TrainingResponseDto result = contentService.createTraining(request);

        assertNotNull(result);
        assertEquals("New Training", result.getTitle());
        assertEquals(0.8, result.getPassingThreshold());
        assertEquals(1, result.getSecurityLevel());
        verify(auditLogService).logEvent(eq("TRAINING_CREATED"), anyString());
    }

    @Test
    void shouldThrowException_whenUpdatingTrainingWithOldVersion() {
        CreateTrainingRequest request = new CreateTrainingRequest();
        request.setVersion(0L);
        training.setVersion(1L);

        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));

        assertThrows(OptimisticLockingFailureException.class,
                () -> contentService.updateTraining(trainingId, request));

        verify(trainingRepository, never()).save(any());
    }

    @Test
    void shouldUpdateTraining_andTriggerProgressReset_whenRequested() {
        CreateTrainingRequest request = new CreateTrainingRequest();
        request.setTitle("Updated Title");
        request.setDescription("Desc");
        request.setType(TrainingType.OHS);
        request.setValidityPeriodDays(365);
        request.setVersion(1L);
        request.setResetProgress(true);

        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        when(trainingRepository.save(any(Training.class))).thenReturn(training);

        contentService.updateTraining(trainingId, request);

        verify(progressService).resetProgressForTraining(training);
        verify(auditLogService).logEvent(eq("TRAINING_UPDATED"), anyString());
    }

    @Test
    void shouldThrowException_whenDeletingModuleUsedByActiveUsers() {
        UserTrainingStatus activeStatus = UserTrainingStatus.builder()
                .currentModule(module)
                .build();

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(userTrainingStatusRepository.findAllByTrainingId(trainingId))
                .thenReturn(List.of(activeStatus));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> contentService.deleteModule(moduleId));

        assertEquals("Cannot delete active module.", ex.getMessage());
        verify(moduleRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteModule_whenNoUsersAreActiveOnIt() {
        UserTrainingStatus finishedStatus = UserTrainingStatus.builder()
                .currentModule(null)
                .build();

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(userTrainingStatusRepository.findAllByTrainingId(trainingId))
                .thenReturn(List.of(finishedStatus));

        contentService.deleteModule(moduleId);

        verify(moduleRepository).delete(module);
        verify(trainingRepository).save(training);
        verify(auditLogService).logEvent(eq("MODULE_DELETED"), anyString());
    }

    @Test
    void shouldReorderModules_whenOrderIndexChanges() {
        UpdateModuleRequest request = new UpdateModuleRequest();
        request.setTitle("Moved Module");
        request.setType(ModuleType.VIDEO);
        request.setVersion(1L);
        request.setOrderIndex(1);

        TrainingModule module2 = TrainingModule.builder().id(UUID.randomUUID()).orderIndex(1).training(training).build();
        training.getModules().add(module2);

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        contentService.updateModule(moduleId, request);

        verify(trainingRepository).save(training);
    }
}