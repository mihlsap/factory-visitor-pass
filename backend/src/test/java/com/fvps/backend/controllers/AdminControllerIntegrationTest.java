package com.fvps.backend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvps.backend.domain.dto.training.CreateTrainingRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.fvps.backend.config.TestConfig.class)
class AdminControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private TrainingRepository trainingRepository;
        @Autowired
        private UserTrainingStatusRepository userTrainingStatusRepository;

        @MockitoBean
        private EmailService emailService;
        @MockitoBean
        private FileStorageService fileStorageService;

        @BeforeEach
        void setUp() {
                userTrainingStatusRepository.deleteAll();
                trainingRepository.deleteAll();
                userRepository.deleteAll();
        }

        @Test
        void shouldCreateTraining_Successfully() throws Exception {
                CreateTrainingRequest request = new CreateTrainingRequest();
                request.setTitle("Fire Safety Level 1");
                request.setDescription("Basic fire safety rules");
                request.setType(TrainingType.OHS);
                request.setValidityPeriodDays(365);
                request.setSecurityLevel(1);
                request.setPassingThreshold(0.8);

                mockMvc.perform(post("/api/admin/trainings")
                                .with(user("admin@fvps.com").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title", is("Fire Safety Level 1")))
                                .andExpect(jsonPath("$.id").exists());

                List<Training> trainings = trainingRepository.findAll();
                assertEquals(1, trainings.size());
                assertEquals("Fire Safety Level 1", trainings.getFirst().getTitle());
        }

        @Test
        void shouldAssignTrainingToUser_Successfully() throws Exception {
                User user = User.builder()
                                .email("employee@fvps.com")
                                .password("pass")
                                .name("John").surname("Doe")
                                .role(UserRole.EMPLOYEE)
                                .status(UserStatus.ACTIVE)
                                .build();
                user = userRepository.save(user);

                Training training = Training.builder()
                                .title("Safety")
                                .description("Desc")
                                .type(TrainingType.OHS)
                                .validityPeriodDays(30)
                                .version(1L)
                                .build();
                training = trainingRepository.save(training);

                mockMvc.perform(post("/api/admin/users/" + user.getId() + "/assign/" + training.getId())
                                .with(user("admin@fvps.com").roles("ADMIN")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", is("TRAINING_ASSIGNED")));

                assertTrue(userTrainingStatusRepository.existsByUserIdAndTrainingId(user.getId(), training.getId()));
        }

        @Test
        void shouldChangeUserStatus_andSendEmail() throws Exception {
                User user = User.builder()
                                .email("badguy@fvps.com")
                                .password("pass")
                                .name("Bad").surname("Guy")
                                .role(UserRole.EMPLOYEE)
                                .status(UserStatus.ACTIVE)
                                .build();
                user = userRepository.save(user);

                mockMvc.perform(put("/api/admin/users/" + user.getId() + "/status")
                                .with(user("admin@fvps.com").roles("ADMIN"))
                                .param("status", "BLOCKED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", is("USER_STATUS_CHANGED")));

                User updatedUser = userRepository.findById(user.getId()).orElseThrow();
                assertEquals(UserStatus.BLOCKED, updatedUser.getStatus());

                verify(emailService).sendEmail(eq("badguy@fvps.com"), anyString(), anyString());
        }

        @Test
        void shouldDenyAccess_whenUserIsNotAdmin() throws Exception {
                mockMvc.perform(get("/api/admin/users")
                                .with(user("hacker@fvps.com").roles("USER")))
                                .andExpect(status().isForbidden());
        }

        @Test
        void shouldPreviewUserPass_Successfully() throws Exception {
                User user = User.builder()
                                .email("worker@fvps.com").password("pass").name("Worker").surname("One")
                                .role(UserRole.EMPLOYEE).status(UserStatus.ACTIVE).build();
                user = userRepository.save(user);

                Training training = Training.builder().title("BHP").type(TrainingType.OHS)
                                .validityPeriodDays(365).version(1L).build();
                training = trainingRepository.save(training);

                UserTrainingStatus status = UserTrainingStatus.builder()
                                .user(user).training(training).status(ProgressStatus.COMPLETED)
                                .completedAt(java.time.LocalDateTime.now())
                                .validUntil(java.time.LocalDateTime.now().plusDays(365))
                                .validUntil(java.time.LocalDateTime.now().plusDays(365))
                                .build();
                userTrainingStatusRepository.save(status);

                mockMvc.perform(get("/api/admin/users/" + user.getId() + "/pass-preview")
                                .with(user("admin@fvps.com").roles("ADMIN")))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        }

        @Test
        void shouldChangeUserRole_Successfully() throws Exception {
                User user = User.builder()
                                .email("employee@fvps.com").password("pass").name("John").surname("Doe")
                                .role(UserRole.EMPLOYEE).status(UserStatus.ACTIVE).build();
                user = userRepository.save(user);

                mockMvc.perform(put("/api/admin/users/" + user.getId() + "/role")
                                .with(user("admin@fvps.com").roles("ADMIN"))
                                .param("role", "GUARD"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", is("USER_ROLE_CHANGED")));

                User updatedUser = userRepository.findById(user.getId()).orElseThrow();
                assertEquals(UserRole.GUARD, updatedUser.getRole());
        }

        @Test
        void shouldAssignTrainingByLevel_Successfully() throws Exception {
                User user1 = User.builder().email("u1@fvps.com").password("p").name("U1").surname("S1")
                                .role(UserRole.EMPLOYEE).status(UserStatus.ACTIVE).clearanceLevel(1).build();
                User user2 = User.builder().email("u2@fvps.com").password("p").name("U2").surname("S2")
                                .role(UserRole.EMPLOYEE).status(UserStatus.ACTIVE).clearanceLevel(2).build();
                userRepository.saveAll(List.of(user1, user2));

                Training training = Training.builder().title("Lvl 2 Training").type(TrainingType.OHS)
                                .validityPeriodDays(365).securityLevel(2).version(1L).build();
                training = trainingRepository.save(training);

                mockMvc.perform(post("/api/admin/trainings/" + training.getId() + "/assign-level")
                                .with(user("admin@fvps.com").roles("ADMIN"))
                                .param("level", "2"))
                                .andExpect(status().isOk())
                                .andExpect(status().isOk());

                assertTrue(userTrainingStatusRepository.existsByUserIdAndTrainingId(user2.getId(), training.getId()));
                assertTrue(userTrainingStatusRepository.existsByUserIdAndTrainingId(user1.getId(), training.getId()));
        }
}