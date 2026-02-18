package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private TrainingProgressService trainingProgressService;
    @Mock
    private UserService userService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PdfGeneratorService pdfGeneratorService;

    @InjectMocks
    private AdminServiceImpl adminService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void shouldChangeUserStatus_delegatesToUserService() {
        adminService.changeUserStatus(userId, UserStatus.BLOCKED);
        verify(userService).changeUserStatus(userId, UserStatus.BLOCKED);
    }

    @Test
    void shouldThrowException_whenGeneratingPassForInactiveUser() {
        User user = User.builder().id(userId).status(UserStatus.BLOCKED).build();
        when(userService.getById(userId)).thenReturn(user);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminService.generatePassPdf(userId));

        assertTrue(ex.getMessage().contains("User is not active"));
        verify(pdfGeneratorService, never()).generatePassPdf(any(), any());
    }

    @Test
    void shouldThrowException_whenUserHasNoValidTrainings() {
        User user = User.builder().id(userId).status(UserStatus.ACTIVE).build();
        when(userService.getById(userId)).thenReturn(user);
        when(trainingProgressService.getValidTrainingsForUser(userId)).thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminService.generatePassPdf(userId));

        assertTrue(ex.getMessage().contains("no valid, completed trainings"));
        verify(pdfGeneratorService, never()).generatePassPdf(any(), any());
    }

    @Test
    void shouldGeneratePdf_whenConditionsMet() {
        User user = User.builder().id(userId).status(UserStatus.ACTIVE).build();
        List<UserTrainingDto> trainings = List.of(new UserTrainingDto());
        byte[] expectedPdf = new byte[] { 1, 2, 3 };

        when(userService.getById(userId)).thenReturn(user);
        when(trainingProgressService.getValidTrainingsForUser(userId)).thenReturn(trainings);
        when(pdfGeneratorService.generatePassPdf(user, trainings)).thenReturn(expectedPdf);

        byte[] result = adminService.generatePassPdf(userId);

        assertArrayEquals(expectedPdf, result);
        verify(auditLogService).logEvent(eq(userId), eq("PASS_PREVIEW_GENERATED"), anyString());
    }
}