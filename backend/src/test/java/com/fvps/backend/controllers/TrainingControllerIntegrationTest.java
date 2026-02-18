package com.fvps.backend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
import com.fvps.backend.domain.entities.*;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.domain.enums.TrainingType;
import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import com.fvps.backend.repositories.TrainingModuleRepository;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.EmailService;
import com.fvps.backend.services.FileStorageService;
import com.fvps.backend.services.PassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.fvps.backend.config.TestConfig.class)
class TrainingControllerIntegrationTest {

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
        @Autowired
        private TrainingModuleRepository moduleRepository;

        @MockitoBean
        private EmailService emailService;
        @MockitoBean
        private FileStorageService fileStorageService;
        @MockitoBean
        private PassService passService;

        private User user;
        private Training training;
        private TrainingModule videoModule;
        private TrainingModule quizModule;
        private QuizQuestion question1;

        @BeforeEach
        void setUp() {
                userTrainingStatusRepository.deleteAll();
                moduleRepository.deleteAll();
                trainingRepository.deleteAll();
                userRepository.deleteAll();

                user = User.builder()
                                .email("student@fvps.com")
                                .password("pass")
                                .name("Student")
                                .surname("Testowy")
                                .role(UserRole.EMPLOYEE)
                                .status(UserStatus.ACTIVE)
                                .build();
                user = userRepository.save(user);

                training = Training.builder()
                                .title("Safety Course")
                                .type(TrainingType.OHS)
                                .validityPeriodDays(30)
                                .passingThreshold(0.5)
                                .version(1L)
                                .modules(new ArrayList<>())
                                .build();
                training = trainingRepository.save(training);

                videoModule = TrainingModule.builder()
                                .title("Intro Video")
                                .type(ModuleType.VIDEO)
                                .orderIndex(0)
                                .training(training)
                                .build();
                videoModule = moduleRepository.save(videoModule);

                quizModule = TrainingModule.builder()
                                .title("Final Exam")
                                .type(ModuleType.QUIZ)
                                .orderIndex(1)
                                .training(training)
                                .build();

                question1 = QuizQuestion.builder()
                                .questionText("Is safety important?")
                                .options(List.of("Yes", "No"))
                                .correctOptionIndex(0)
                                .module(quizModule)
                                .orderIndex(0)
                                .build();

                quizModule.setQuestions(List.of(question1));
                quizModule = moduleRepository.save(quizModule);

                UserTrainingStatus status = UserTrainingStatus.builder()
                                .user(user)
                                .training(training)
                                .status(ProgressStatus.IN_PROGRESS)
                                .currentModule(videoModule)
                                .build();
                userTrainingStatusRepository.save(status);
        }

        @Test
        void shouldGetMyTrainings() throws Exception {
                mockMvc.perform(get("/api/trainings/my")
                                .with(user("student@fvps.com").roles("EMPLOYEE")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].training.title", is("Safety Course")));
        }

        @Test
        void shouldCompleteLectureModule_andAdvanceToQuiz() throws Exception {
                mockMvc.perform(post(
                                "/api/trainings/" + training.getId() + "/modules/" + videoModule.getId() + "/complete")
                                .with(user("student@fvps.com").roles("EMPLOYEE")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", is("MODULE_COMPLETED")));

                UserTrainingStatus status = userTrainingStatusRepository
                                .findByUserIdAndTrainingId(user.getId(), training.getId()).orElseThrow();
                assertEquals(quizModule.getId(), status.getCurrentModule().getId());
                assertEquals(ProgressStatus.IN_PROGRESS, status.getStatus());
        }

        @Test
        void shouldPassQuiz_andCompleteTraining() throws Exception {
                UserTrainingStatus status = userTrainingStatusRepository
                                .findByUserIdAndTrainingId(user.getId(), training.getId()).orElseThrow();
                status.setCurrentModule(quizModule);
                userTrainingStatusRepository.save(status);

                QuizSubmissionDto submission = new QuizSubmissionDto(Map.of(question1.getId(), 0));

                mockMvc.perform(post(
                                "/api/trainings/" + training.getId() + "/modules/" + quizModule.getId()
                                                + "/submit-quiz")
                                .with(user("student@fvps.com").roles("EMPLOYEE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(submission)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.passed", is(true)));

                UserTrainingStatus updatedStatus = userTrainingStatusRepository
                                .findByUserIdAndTrainingId(user.getId(), training.getId()).orElseThrow();
                assertEquals(ProgressStatus.COMPLETED, updatedStatus.getStatus());
                assertNotNull(updatedStatus.getCompletedAt());

                verify(passService).sendPassCompletionNotification(any(User.class));
        }

        @Test
        void shouldFailQuiz_andSetStatusFailed() throws Exception {
                UserTrainingStatus status = userTrainingStatusRepository
                                .findByUserIdAndTrainingId(user.getId(), training.getId()).orElseThrow();
                status.setCurrentModule(quizModule);
                userTrainingStatusRepository.save(status);

                QuizSubmissionDto submission = new QuizSubmissionDto(Map.of(question1.getId(), 1));

                mockMvc.perform(post(
                                "/api/trainings/" + training.getId() + "/modules/" + quizModule.getId()
                                                + "/submit-quiz")
                                .with(user("student@fvps.com").roles("EMPLOYEE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(submission)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.passed", is(false)));

                UserTrainingStatus updatedStatus = userTrainingStatusRepository
                                .findByUserIdAndTrainingId(user.getId(), training.getId()).orElseThrow();
                assertEquals(ProgressStatus.FAILED, updatedStatus.getStatus());

        }
}