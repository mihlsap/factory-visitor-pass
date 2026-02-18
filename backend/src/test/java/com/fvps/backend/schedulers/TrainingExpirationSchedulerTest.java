package com.fvps.backend.schedulers;

import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingExpirationSchedulerTest {

    @Mock
    private UserTrainingStatusRepository statusRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private Clock clock;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private TrainingExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        lenient().when(clock.instant()).thenReturn(Instant.parse("2025-01-01T12:00:00Z"));

        lenient().when(messageSource.getMessage(eq("email.expiration.subject"), any(), any()))
                .thenReturn("Action Required: Training Expiring Soon");

        lenient().when(messageSource.getMessage(eq("email.expiration.body"), any(), any()))
                .thenReturn("Email Body Content");
    }

    @Test
    void shouldNotifyUsers_whenTrainingsAreExpiring() {
        User user1 = User.builder().id(UUID.randomUUID()).email("user1@example.com").name("User One").build();
        User user2 = User.builder().id(UUID.randomUUID()).email("user2@example.com").name("User Two").build();

        Training training = Training.builder().title("Safety Course").build();

        UserTrainingStatus status1 = UserTrainingStatus.builder().user(user1).training(training).build();
        UserTrainingStatus status2 = UserTrainingStatus.builder().user(user2).training(training).build();

        when(statusRepository.findAllByStatusAndValidUntilBetween(eq(ProgressStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(status1, status2))
                .thenReturn(Collections.emptyList());

        scheduler.notifyUpcomingExpirations();

        verify(emailService).sendEmail(eq("user1@example.com"), contains("Expiring Soon"), anyString());
        verify(emailService).sendEmail(eq("user2@example.com"), contains("Expiring Soon"), anyString());

        verify(auditLogService, times(2)).logEvent(any(), eq("NOTIFICATION_SENT"), anyString());
    }

    @Test
    void shouldContinueProcessing_whenEmailSendingFailsForOneUser() {
        User user1 = User.builder().id(UUID.randomUUID()).email("user1@example.com").build();
        User user2 = User.builder().id(UUID.randomUUID()).email("user2@example.com").build();
        Training training = Training.builder().title("Safety Course").build();

        UserTrainingStatus status1 = UserTrainingStatus.builder().user(user1).training(training).build();
        UserTrainingStatus status2 = UserTrainingStatus.builder().user(user2).training(training).build();

        when(statusRepository.findAllByStatusAndValidUntilBetween(eq(ProgressStatus.COMPLETED), any(), any()))
                .thenReturn(List.of(status1, status2))
                .thenReturn(Collections.emptyList());

        doThrow(new RuntimeException("SMTP Error"))
                .when(emailService).sendEmail(eq("user1@example.com"), anyString(), anyString());

        scheduler.notifyUpcomingExpirations();

        verify(emailService).sendEmail(eq("user1@example.com"), anyString(), anyString());

        verify(emailService).sendEmail(eq("user2@example.com"), anyString(), anyString());

        verify(auditLogService).logEvent(eq(user2.getId()), eq("NOTIFICATION_SENT"), anyString());
    }

    @Test
    void shouldDoNothing_whenNoTrainingsExpiring() {
        when(statusRepository.findAllByStatusAndValidUntilBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.notifyUpcomingExpirations();

        verifyNoInteractions(emailService);
    }

    @Test
    void shouldExpireTrainings_whenValidityDatePassed() {
        User user = User.builder().id(UUID.randomUUID()).build();
        Training training = Training.builder().id(UUID.randomUUID()).build();
        UserTrainingStatus status = UserTrainingStatus.builder()
                .user(user)
                .training(training)
                .status(ProgressStatus.COMPLETED)
                .validUntil(java.time.LocalDateTime.now(clock).minusMinutes(1))
                .build();

        when(statusRepository.findAllByStatusAndValidUntilBefore(eq(ProgressStatus.COMPLETED), any()))
                .thenReturn(List.of(status));

        scheduler.expireTrainings();

        assertEquals(ProgressStatus.EXPIRED, status.getStatus());
        verify(statusRepository).save(status);
        verify(auditLogService).logEvent(eq(user.getId()), eq("TRAINING_EXPIRED"), anyString());
    }
}