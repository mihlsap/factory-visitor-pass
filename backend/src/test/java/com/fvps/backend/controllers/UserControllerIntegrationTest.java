package com.fvps.backend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvps.backend.domain.dto.auth.ChangePasswordRequest;
import com.fvps.backend.domain.dto.user.UpdateUserRequest;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.domain.enums.TrainingType;
import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.EmailService;
import com.fvps.backend.services.FileStorageService;
import com.fvps.backend.services.PdfGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.fvps.backend.config.TestConfig.class)
class UserControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private TrainingRepository trainingRepository;
        @Autowired
        private UserTrainingStatusRepository userTrainingStatusRepository;

        @MockitoBean
        private EmailService emailService;
        @MockitoBean
        private FileStorageService fileStorageService;
        @MockitoBean
        private PdfGeneratorService pdfGeneratorService;

        private User user;

        @BeforeEach
        void setUp() {
                userTrainingStatusRepository.deleteAll();
                trainingRepository.deleteAll();
                userRepository.deleteAll();

                user = User.builder()
                                .email("user@fvps.com")
                                .password(passwordEncoder.encode("OldPass1!"))
                                .name("Jan")
                                .surname("Kowalski")
                                .role(UserRole.EMPLOYEE)
                                .status(UserStatus.ACTIVE)
                                .version(1L)
                                .build();
                user = userRepository.save(user);
        }

        @Test
        void shouldGetMyProfile_Successfully() throws Exception {
                mockMvc.perform(get("/api/users/me")
                                .with(user("user@fvps.com").roles("EMPLOYEE")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email", is("user@fvps.com")))
                                .andExpect(jsonPath("$.name", is("Jan")));
        }

        @Test
        void shouldUpdateProfile_Successfully() throws Exception {
                UpdateUserRequest request = new UpdateUserRequest();
                request.setName("Janusz");
                request.setSurname("Nowak");
                request.setVersion(1L);

                MockMultipartFile data = new MockMultipartFile(
                                "data", "", "application/json",
                                objectMapper.writeValueAsBytes(request));

                mockMvc.perform(multipart("/api/users/me")
                                .file(data)
                                .with(user("user@fvps.com").roles("EMPLOYEE"))
                                .with(req -> {
                                        req.setMethod("PUT");
                                        return req;
                                }))
                                .andExpect(status().isOk());

                User updated = userRepository.findById(user.getId()).orElseThrow();
                assertEquals("Janusz", updated.getName());
                assertEquals("Nowak", updated.getSurname());
        }

        @Test
        void shouldChangePassword_Successfully() throws Exception {
                ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "NewPass1!");

                mockMvc.perform(patch("/api/users/me/password")
                                .with(user("user@fvps.com").roles("EMPLOYEE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                User updated = userRepository.findById(user.getId()).orElseThrow();
                assertTrue(passwordEncoder.matches("NewPass1!", updated.getPassword()));
        }

        @Test
        void shouldFailPassDownload_whenNoTrainings() throws Exception {

                mockMvc.perform(get("/api/users/me/download-pass")
                                .with(user("user@fvps.com").roles("EMPLOYEE")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldDownloadPass_whenTrainingsCompleted() throws Exception {
                Training training = Training.builder()
                                .title("BHP")
                                .type(TrainingType.OHS)
                                .validityPeriodDays(365)
                                .version(1L)
                                .build();
                training = trainingRepository.save(training);

                UserTrainingStatus status = UserTrainingStatus.builder()
                                .user(user)
                                .training(training)
                                .status(ProgressStatus.COMPLETED)
                                .completedAt(LocalDateTime.now())
                                .validUntil(LocalDateTime.now().plusDays(365))
                                .build();
                userTrainingStatusRepository.save(status);

                when(pdfGeneratorService.generatePassPdf(any(), any())).thenReturn(new byte[] { 1, 2, 3 });

                mockMvc.perform(get("/api/users/me/download-pass")
                                .with(user("user@fvps.com").roles("EMPLOYEE")))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                                .andExpect(header().string("Content-Disposition", "inline; filename=My_FVPS_Pass.pdf"));
        }
}