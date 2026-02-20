package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.auth.ChangePasswordRequest;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.dto.user.UpdateUserRequest;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PdfGeneratorService pdfGeneratorService;
    @Mock private TrainingProgressService trainingProgressService;
    @Mock private UserClearanceService userClearanceService;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Clock clock;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private final UUID userId = UUID.randomUUID();
    private final String email = "john@example.com";
    private final LocalDateTime FIXED_NOW = LocalDateTime.of(2025, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(userService, "maxFailedAttempts", 3);
        ReflectionTestUtils.setField(userService, "lockTimeMinutes", 15);

        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant());

        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Message");

        lenient().when(messageSource.getMessage(eq("email.account.deleted.subject"), any(), any()))
                .thenReturn("FVPS - Account Deactivated");
        lenient().when(messageSource.getMessage(eq("email.account.deleted.body"), any(), any()))
                .thenReturn("Body content");

        lenient().when(messageSource.getMessage(eq("email.account.blocked.subject"), any(), any()))
                .thenReturn("FVPS - Account Suspended");
        lenient().when(messageSource.getMessage(eq("email.account.blocked.body"), any(), any()))
                .thenReturn("Body content");

        lenient().when(messageSource.getMessage(eq("email.account.activated.subject"), any(), any()))
                .thenReturn("FVPS - Account Activated");
        lenient().when(messageSource.getMessage(eq("email.account.activated.body"), any(), any()))
                .thenReturn("Body content");

        user = User.builder()
                .id(userId)
                .email(email)
                .name("John")
                .surname("Doe")
                .status(UserStatus.ACTIVE)
                .version(1L)
                .build();
    }

    @Test
    void shouldSoftDeleteUser_andSendNotification() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.changeUserStatus(userId, UserStatus.DELETED);

        assertEquals(UserStatus.DELETED, user.getStatus());
        verify(userRepository).save(user);
        verify(emailService).sendEmail(eq(email), contains("Deactivated"), anyString());
    }

    @Test
    void shouldBlockUser_andSendNotification() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.changeUserStatus(userId, UserStatus.BLOCKED);

        assertEquals(UserStatus.BLOCKED, user.getStatus());
        verify(emailService).sendEmail(eq(email), contains("Suspended"), anyString());
    }

    @Test
    void shouldActivateUser_andResetLockoutCounters() {
        user.setStatus(UserStatus.BLOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockoutTime(LocalDateTime.now(clock).plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.changeUserStatus(userId, UserStatus.ACTIVE);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockoutTime());
        verify(emailService).sendEmail(eq(email), contains("Activated"), anyString());
    }

    @Test
    void shouldGeneratePassPdf_whenUserIsActiveAndHasTrainings() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(trainingProgressService.getValidTrainingsForUser(userId))
                .thenReturn(List.of(new UserTrainingDto()));

        when(pdfGeneratorService.generatePassPdf(any(), anyList())).thenReturn(new byte[]{1, 2, 3});

        byte[] result = userService.generateMyPassPdf(email);

        assertNotNull(result);
        verify(userClearanceService).recalculateUserClearance(userId);
        verify(auditLogService).logEvent(eq(userId), eq("PASS_DOWNLOADED"), anyString());
    }

    @Test
    void shouldThrowException_whenGeneratingPassForInactiveUser() {
        user.setStatus(UserStatus.BLOCKED);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> userService.generateMyPassPdf(email));
        verify(pdfGeneratorService, never()).generatePassPdf(any(), any());
    }

    @Test
    void shouldThrowException_whenUserHasNoTrainings() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(trainingProgressService.getValidTrainingsForUser(userId))
                .thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.generateMyPassPdf(email));
        assertTrue(ex.getMessage().contains("no valid, completed trainings"));
    }

    @Test
    void shouldThrowOptimisticLockingException_whenVersionMismatch() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("NewName");
        request.setVersion(2L);

        user.setVersion(1L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(OptimisticLockingFailureException.class,
                () -> userService.updateUserData(email, request, null));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLockAccount_afterMaxFailedAttempts() {
        user.setFailedLoginAttempts(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.registerFailedLogin(userId);

        assertEquals(3, user.getFailedLoginAttempts());
        assertNotNull(user.getLockoutTime());
        assertEquals(FIXED_NOW.plusMinutes(15), user.getLockoutTime());
        verify(auditLogService).logEvent(eq(userId), eq("ACCOUNT_LOCKED"), anyString());
    }

    @Test
    void shouldChangePassword_whenOldPasswordIsCorrect() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass", "newPass");
        user.setPassword("encodedOldPass");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "encodedOldPass")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");

        userService.changePassword(email, request);

        verify(userRepository).save(user);
        verify(auditLogService).logEvent(eq(userId), eq("PASSWORD_CHANGED"), anyString());
    }

    @Test
    void shouldThrowException_whenChangingPasswordWithWrongOldPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongPass", "newPass");
        user.setPassword("encodedOldPass");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "encodedOldPass")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> userService.changePassword(email, request));
        verify(auditLogService).logEvent(eq(userId), eq("PASSWORD_CHANGE_FAILED"), anyString());
    }
}