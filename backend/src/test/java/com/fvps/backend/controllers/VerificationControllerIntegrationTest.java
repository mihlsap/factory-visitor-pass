package com.fvps.backend.controllers;

import com.fvps.backend.domain.dto.verification.VerificationResponse;
import com.fvps.backend.services.VerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private VerificationService verificationService;

        @Value("${app.endpoints.verify:/api/verify}")
        private String verifyEndpoint;

        @Test
        void shouldGrantAccess_whenServiceReturnsAllowed() throws Exception {
                UUID userId = UUID.randomUUID();
                VerificationResponse response = VerificationResponse.builder()
                                .accessGranted(true)
                                .message("ACCESS_GRANTED")
                                .fullName("Jan Kowalski")
                                .photoUrl("photo.jpg")
                                .build();

                when(verificationService.verifyUserAccess(eq(userId), anyInt())).thenReturn(response);

                mockMvc.perform(get(verifyEndpoint + "/" + userId)
                                .with(user("guard").roles("GUARD"))
                                .param("level", "1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessGranted", is(true)))
                                .andExpect(jsonPath("$.fullName", is("Jan Kowalski")));
        }

        @Test
        void shouldDenyAccess_whenServiceReturnsDenied() throws Exception {
                UUID userId = UUID.randomUUID();
                VerificationResponse response = VerificationResponse.builder()
                                .accessGranted(false)
                                .message("ACCESS_DENIED_EXPIRED")
                                .build();

                when(verificationService.verifyUserAccess(eq(userId), anyInt())).thenReturn(response);

                mockMvc.perform(get(verifyEndpoint + "/" + userId)
                                .with(user("guard").roles("GUARD"))
                                .param("level", "1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessGranted", is(false)))
                                .andExpect(jsonPath("$.message", is("ACCESS_DENIED_EXPIRED")));
        }
}