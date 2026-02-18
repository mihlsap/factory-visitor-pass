package com.fvps.backend.listeners;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.events.UserStatusChangedEvent;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.PassService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.notification.retry-delay=10")
@ActiveProfiles("test")
class NotificationListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private PassService passService;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void shouldHandleEventAsynchronously() {
        User user = User.builder().id(UUID.randomUUID()).email("async@test.com").name("Async User").build();
        UserStatusChangedEvent event = new UserStatusChangedEvent(this, user, "Test Reason");

        eventPublisher.publishEvent(event);

        verify(passService, timeout(2000).times(1))
                .sendStatusChangeNotification(eq(user), eq("Test Reason"));

        verify(auditLogService, timeout(2000)).logEvent(eq(user.getId()), eq("ASYNC_PROCESS_STARTED"), anyString());
    }

    @Test
    void shouldRetry5Times_andThenRecover_whenServiceFails() {
        User user = User.builder().id(UUID.randomUUID()).email("retry@test.com").name("Retry User").build();
        UserStatusChangedEvent event = new UserStatusChangedEvent(this, user, "Failure Reason");

        doThrow(new RuntimeException("SMTP Server Down"))
                .when(passService).sendStatusChangeNotification(any(), any());

        eventPublisher.publishEvent(event);

        verify(passService, timeout(2000).times(5))
                .sendStatusChangeNotification(eq(user), eq("Failure Reason"));

        verify(auditLogService, timeout(2000)).logEvent(
                eq(user.getId()),
                eq("NOTIFICATION_FAILED"),
                contains("SMTP Server Down")
        );
    }
}