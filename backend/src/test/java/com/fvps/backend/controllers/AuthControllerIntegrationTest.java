package com.fvps.backend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvps.backend.domain.dto.auth.LoginRequest;
import com.fvps.backend.domain.dto.auth.RegisterRequest;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.EmailService;
import com.fvps.backend.services.FileStorageService;
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

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.fvps.backend.config.TestConfig.class)
class AuthControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserTrainingStatusRepository userTrainingStatusRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private EmailService emailService;

        @MockitoBean
        private FileStorageService fileStorageService;

        @BeforeEach
        void setUp() {
                userTrainingStatusRepository.deleteAll();
                userRepository.deleteAll();
        }

        @Test
        void shouldRegisterNewUser_Successfully() throws Exception {
                MockMultipartFile photo = new MockMultipartFile(
                                "photo", "avatar.jpg", "image/jpeg", "fake-image-content".getBytes());

                MockMultipartFile data = new MockMultipartFile(
                                "data", "", "application/json",
                                objectMapper.writeValueAsBytes(RegisterRequest.builder()
                                                .email("newuser@fvps.com")
                                                .password("StrongPass1!")
                                                .name("Jan")
                                                .surname("Kowalski")
                                                .phoneNumber("+48123456789")
                                                .build()));

                when(fileStorageService.savePhoto(any())).thenReturn("saved-photo.jpg");

                mockMvc.perform(multipart("/api/auth/register")
                                .file(photo)
                                .file(data))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role", is("EMPLOYEE")))
                                .andExpect(jsonPath("$.token").exists());

                assertTrue(userRepository.findByEmail("newuser@fvps.com").isPresent());
        }

        @Test
        void shouldFailRegistration_whenEmailAlreadyExists() throws Exception {
                User existingUser = User.builder()
                                .email("exists@example.com")
                                .password("pass")
                                .name("Old")
                                .surname("User")
                                .role(UserRole.GUEST)
                                .status(UserStatus.ACTIVE)
                                .build();
                userRepository.save(existingUser);

                MockMultipartFile photo = new MockMultipartFile("photo", "img.jpg", "image/jpeg", new byte[1]);
                MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                                objectMapper.writeValueAsBytes(RegisterRequest.builder()
                                                .email("exists@example.com")
                                                .password("NewPass1!")
                                                .name("New")
                                                .surname("Guy")
                                                .build()));

                mockMvc.perform(multipart("/api/auth/register").file(photo).file(data))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldLoginSuccessfully_andReturnMfaEnabled() throws Exception {
                User user = User.builder()
                                .email("login@test.com")
                                .password(passwordEncoder.encode("MySecretPass1!"))
                                .name("Test")
                                .surname("Login")
                                .role(UserRole.EMPLOYEE)
                                .status(UserStatus.ACTIVE)
                                .build();
                userRepository.save(user);

                LoginRequest loginRequest = new LoginRequest("login@test.com", "MySecretPass1!");

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.mfaEnabled", is(true)))
                                .andExpect(jsonPath("$.token").isEmpty());

                User updatedUser = userRepository.findByEmail("login@test.com").orElseThrow();
                assertNotNull(updatedUser.getTwoFactorCode());
        }

        @Test
        void shouldFailLogin_whenPasswordIsWrong() throws Exception {
                User user = User.builder()
                                .email("wrongpass@test.com")
                                .password(passwordEncoder.encode("CorrectPass1!"))
                                .name("Test")
                                .surname("User")
                                .role(UserRole.EMPLOYEE)
                                .status(UserStatus.ACTIVE)
                                .build();
                userRepository.save(user);

                LoginRequest loginRequest = new LoginRequest("wrongpass@test.com", "WrongOne!");

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());

                User failedUser = userRepository.findByEmail("wrongpass@test.com").orElseThrow();
                assertTrue(failedUser.getFailedLoginAttempts() > 0);
        }
}