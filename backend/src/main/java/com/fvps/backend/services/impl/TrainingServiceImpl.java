package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.QuizSubmissionDto;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.*;
import com.fvps.backend.domain.enums.ModuleType;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.repositories.TrainingRepository;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.repositories.UserTrainingStatusRepository;
import com.fvps.backend.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingServiceImpl implements TrainingService {

    private final TrainingRepository trainingRepository;
    private final UserRepository userRepository;
    private final UserTrainingStatusRepository userTrainingStatusRepository;

    // ... (metody CRUD bez zmian, zostaw je tak jak były) ...
    @Override
    @Transactional
    public Training createTraining(Training training) {
        if (training.getModules() != null) {
            training.getModules().forEach(module -> {
                module.setTraining(training);
                if (module.getQuestions() != null) {
                    module.getQuestions().forEach(q -> q.setModule(module));
                }
            });
        }
        return trainingRepository.save(training);
    }

    @Override
    public List<Training> getAllTrainings() { return trainingRepository.findAll(); }

    @Override
    public Training getTrainingById(UUID id) { return trainingRepository.findById(id).orElseThrow(); }

    @Override
    public void deleteTraining(UUID id) { trainingRepository.deleteById(id); }

    @Override
    @Transactional
    public void assignTrainingToUser(UUID userId, UUID trainingId) {
        User user = userRepository.findById(userId).orElseThrow();
        Training training = trainingRepository.findById(trainingId).orElseThrow();
        UserTrainingStatus status = UserTrainingStatus.builder().user(user).training(training).build();
        userTrainingStatusRepository.save(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrainingDto> getUserTrainings(String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElseThrow();
        return userTrainingStatusRepository.findByUserId(user.getId()).stream()
                .map(status -> UserTrainingDto.builder()
                        .id(status.getId())
                        .training(status.getTraining())
                        .status(status.getStatus())
                        .currentModuleIndex(status.getCurrentModuleIndex())
                        .quizScore(status.getQuizScore())
                        .completedAt(status.getCompletedAt())
                        .validUntil(status.getValidUntil())
                        .isPassRevoked(status.isPassRevoked())
                        .build())
                .collect(Collectors.toList());
    }

    // --- IMPLEMENTACJA NOWYCH METOD ---

    @Override
    @Transactional
    public void completeModule(String userEmail, UUID trainingId, UUID moduleId) {
        // 1. Pobierz status użytkownika dla tego szkolenia
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);

        // 2. Znajdź aktualny moduł w szkoleniu
        Training training = status.getTraining();
        List<TrainingModule> modules = training.getModules();

        // Sprawdź czy index nie wychodzi poza zakres
        if (status.getCurrentModuleIndex() >= modules.size()) {
            return; // Szkolenie już chyba skończone
        }

        TrainingModule currentModule = modules.get(status.getCurrentModuleIndex());

        // 3. ANTY-CHEAT: Sprawdź, czy użytkownik próbuje zaliczyć WŁAŚCIWY moduł
        // (nie może przeskoczyć z 1 do 5)
        if (!currentModule.getId().equals(moduleId)) {
            throw new IllegalArgumentException("Nie możesz pominąć modułów! Ukończ najpierw: " + currentModule.getTitle());
        }

        // 4. Jeśli to Quiz, nie można go zaliczyć tą metodą (trzeba użyć submitQuiz)
        if (currentModule.getType() == ModuleType.QUIZ) {
            throw new IllegalArgumentException("Quiz musi zostać rozwiązany, a nie pominięty.");
        }

        // 5. Przesuń indeks do przodu
        advanceProgress(status, modules.size());
    }

    @Override
    @Transactional
    public boolean submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission) {
        UserTrainingStatus status = getUserTrainingStatus(userEmail, trainingId);
        Training training = status.getTraining();
        List<TrainingModule> modules = training.getModules();

        if (status.getCurrentModuleIndex() >= modules.size()) {
            throw new IllegalArgumentException("Szkolenie już ukończone.");
        }

        TrainingModule currentModule = modules.get(status.getCurrentModuleIndex());

        // Walidacja ID i Typu
        if (!currentModule.getId().equals(moduleId) || currentModule.getType() != ModuleType.QUIZ) {
            throw new IllegalArgumentException("Nieprawidłowy moduł lub próba oszustwa.");
        }

        // --- SPRAWDZANIE ODPOWIEDZI ---
        List<QuizQuestion> questions = currentModule.getQuestions();
        List<Integer> userAnswers = submission.getAnswers();

        if (userAnswers == null || userAnswers.size() != questions.size()) {
            throw new IllegalArgumentException("Liczba odpowiedzi nie zgadza się z liczbą pytań.");
        }

        int correctCount = 0;
        for (int i = 0; i < questions.size(); i++) {
            // Porównujemy index wybrany przez usera z poprawnym z bazy
            if (userAnswers.get(i) == questions.get(i).getCorrectOptionIndex()) {
                correctCount++;
            }
        }

        double score = (double) correctCount / questions.size();
        status.setQuizScore(score); // Zapisujemy wynik (np. 0.8)

        // Próg zaliczenia: 80%
        if (score >= 0.8) {
            // ZDAŁ -> Przesuwamy dalej (co zazwyczaj oznacza koniec szkolenia)
            advanceProgress(status, modules.size());
            return true;
        } else {
            // OBLAŁ -> Status FAILED, ale nie przesuwamy indexu (musi powtórzyć)
            status.setStatus(ProgressStatus.FAILED);
            userTrainingStatusRepository.save(status);
            return false;
        }
    }

    // --- Metody Pomocnicze ---

    private UserTrainingStatus getUserTrainingStatus(String email, UUID trainingId) {
        User user = userRepository.findByEmail(email).orElseThrow();
        // Pobieramy status konkretnego szkolenia dla usera (filtrowanie po javie lub query, tu prościej tak:)
        return userTrainingStatusRepository.findByUserId(user.getId()).stream()
                .filter(s -> s.getTraining().getId().equals(trainingId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nie jesteś zapisany na to szkolenie."));
    }

    private void advanceProgress(UserTrainingStatus status, int totalModulesCount) {
        // Zwiększamy licznik
        int nextIndex = status.getCurrentModuleIndex() + 1;
        status.setCurrentModuleIndex(nextIndex);
        status.setStatus(ProgressStatus.IN_PROGRESS);

        // Czy to był ostatni moduł?
        if (nextIndex >= totalModulesCount) {
            status.setStatus(ProgressStatus.COMPLETED);
            status.setCompletedAt(LocalDateTime.now());

            // Obliczamy ważność (np. dzisiaj + 365 dni)
            int validDays = status.getTraining().getValidityPeriodDays();
            status.setValidUntil(LocalDateTime.now().plusDays(validDays));
        }

        userTrainingStatusRepository.save(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrainingDto> getValidTrainingsForUser(UUID userId) {
        // 1. Sprawdź czy user istnieje
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("Użytkownik nie znaleziony");
        }

        // 2. Pobierz wszystko i przefiltruj
        return userTrainingStatusRepository.findByUserId(userId).stream()
                .filter(status -> status.getStatus() == ProgressStatus.COMPLETED) // Tylko ukończone
                .filter(status -> status.getValidUntil() != null && status.getValidUntil().isAfter(LocalDateTime.now())) // Tylko ważne
                .map(status -> UserTrainingDto.builder() // Mapujemy na DTO
                        .id(status.getId())
                        .training(status.getTraining())
                        .status(status.getStatus())
                        .completedAt(status.getCompletedAt())
                        .validUntil(status.getValidUntil())
                        .build())
                .collect(Collectors.toList());
    }
}