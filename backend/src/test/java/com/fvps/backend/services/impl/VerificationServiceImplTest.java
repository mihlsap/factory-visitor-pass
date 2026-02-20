package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.verification.VerificationResponse;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.AppMessage;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.UserClearanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserClearanceService userClearanceService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private VerificationServiceImpl verificationService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void shouldDenyAccess_whenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        VerificationResponse response = verificationService.verifyUserAccess(userId, 1);

        assertFalse(response.isAccessGranted());
        assertEquals(AppMessage.ACCESS_DENIED_USER_NOT_FOUND.name(), response.getMessage());
        verify(userClearanceService, never()).recalculateUserClearance(any());
    }

    @Test
    void shouldDenyAccess_whenUserIsNotActive() {
        User user = User.builder().id(userId).status(UserStatus.BLOCKED).name("John").surname("Doe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        VerificationResponse response = verificationService.verifyUserAccess(userId, 1);

        assertFalse(response.isAccessGranted());
        assertEquals(AppMessage.ACCESS_DENIED_BLOCKED.name(), response.getMessage());
        verify(auditLogService).logEvent(eq(userId), eq(AppMessage.ACCESS_DENIED_BLOCKED.name()), contains("inactive"));
    }

    @Test
    void shouldDenyAccess_whenClearanceLevelIsInsufficient() {
        User user = User.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .clearanceLevel(1)
                .name("John").surname("Doe")
                .build();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        VerificationResponse response = verificationService.verifyUserAccess(userId, 2);

        assertFalse(response.isAccessGranted());
        assertEquals(AppMessage.ACCESS_DENIED_LOW_LEVEL.name(), response.getMessage());

        verify(userClearanceService).recalculateUserClearance(userId);
        verify(auditLogService).logEvent(eq(userId), eq(AppMessage.ACCESS_DENIED_LOW_LEVEL.name()),
                contains("Insufficient clearance"));
    }

    @Test
    void shouldGrantAccess_whenClearanceLevelIsSufficient() {
        User user = User.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .clearanceLevel(2)
                .name("John").surname("Doe")
                .photoUrl("photo.jpg")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        VerificationResponse response = verificationService.verifyUserAccess(userId, 1);

        assertTrue(response.isAccessGranted());
        assertEquals(AppMessage.ACCESS_GRANTED.name(), response.getMessage());
        assertEquals("John Doe", response.getFullName());
        assertEquals("photo.jpg", response.getPhotoUrl());

        verify(userClearanceService).recalculateUserClearance(userId);
        verify(auditLogService).logEvent(eq(userId), eq(AppMessage.ACCESS_GRANTED.name()), anyString());
    }
}