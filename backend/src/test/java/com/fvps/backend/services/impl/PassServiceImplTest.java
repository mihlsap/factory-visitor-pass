package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PassServiceImplTest {

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private PassServiceImpl passService;

    @Captor
    private ArgumentCaptor<String> emailBodyCaptor;

    private User user;
    private final String frontendUrl = "http://localhost:5173";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passService, "frontendUrl", frontendUrl);

        lenient().when(messageSource.getMessage(eq("email.pass.completion.subject"), any(), any()))
                .thenReturn("Training Completed - Check your Pass");
        lenient().when(messageSource.getMessage(eq("email.pass.completion.body"), any(), any()))
                .thenAnswer(inv -> "Hello " + ((Object[]) inv.getArgument(1))[0] + "... " + frontendUrl
                        + "/my-pass");

        lenient().when(messageSource.getMessage(eq("email.status.change.subject"), any(), any()))
                .thenReturn("FVPS - Status Update");
        lenient().when(messageSource.getMessage(eq("email.status.change.body"), any(), any()))
                .thenAnswer(inv -> "Reason: " + ((Object[]) inv.getArgument(1))[1] + "... "
                        + frontendUrl + "/login");

        user = User.builder()
                .id(UUID.randomUUID())
                .email("jan@example.com")
                .name("Jan")
                .build();
    }

    @Test
    void shouldSendPassCompletionEmail_withCorrectLinkAndName() {
        passService.sendPassCompletionNotification(user);

        verify(emailService).sendEmail(eq("jan@example.com"), eq("Training Completed - Check your Pass"),
                emailBodyCaptor.capture());

        String sentBody = emailBodyCaptor.getValue();
        assertTrue(sentBody.contains("Hello Jan"));
        assertTrue(sentBody.contains(frontendUrl + "/my-pass"));

        verify(auditLogService).logEvent(eq(user.getId()), eq("COMPLETION_NOTIFICATION_SENT"), anyString());
    }

    @Test
    void shouldSendStatusChangeEmail_withReason() {
        String reason = "Security Policy Update";

        passService.sendStatusChangeNotification(user, reason);

        verify(emailService).sendEmail(eq("jan@example.com"), eq("FVPS - Status Update"),
                emailBodyCaptor.capture());

        String sentBody = emailBodyCaptor.getValue();
        assertTrue(sentBody.contains("Reason: " + reason));
        assertTrue(sentBody.contains(frontendUrl + "/login"));
    }
}