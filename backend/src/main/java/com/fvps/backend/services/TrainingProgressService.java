package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.entities.TrainingModule;
import com.fvps.backend.domain.enums.ProgressStatus;
import com.fvps.backend.domain.enums.ResetMode;
import com.fvps.backend.domain.enums.TrainingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing the runtime state of user trainings.
 * <p>
 * While {@code TrainingContentService} manages the static definitions (CMS),
 * this service handles the dynamic relationship between Users and Trainings.
 * It tracks progress, validates quiz answers, calculates scores, and manages
 * the lifecycle of assignments (Assign -> In Progress -> Completed/Failed).
 * </p>
 */
public interface TrainingProgressService {

    /**
     * Assigns a specific training to a user.
     * <p>
     * Sets the status to {@code NOT_STARTED} and points the user to the first
     * module.
     * </p>
     *
     * @param userId     the UUID of the user.
     * @param trainingId the UUID of the training.
     * @throws IllegalStateException if the user is already assigned to this
     *                               training.
     */
    void assignTrainingToUser(UUID userId, UUID trainingId);

    /**
     * Removes a training assignment from a user.
     * <p>
     * Completely deletes the progress record. Used for correcting administrative
     * errors.
     * </p>
     *
     * @param userId     the UUID of the user.
     * @param trainingId the UUID of the training.
     */
    void unassignTrainingFromUser(UUID userId, UUID trainingId);

    /**
     * Retrieves all trainings assigned to a user (lookup by ID).
     *
     * @param userId   the UUID of the user.
     * @param pageable pagination info.
     * @return a page of training progress DTOs.
     */
    Page<UserTrainingDto> getUserTrainingsByUserId(UUID userId, Pageable pageable);

    /**
     * Retrieves only the trainings that are currently valid and completed.
     * <p>
     * <b>Crucial:</b> This method is the source of truth for generating the Visitor
     * Pass.
     * It filters out expired trainings, failed attempts, or revoked certifications.
     * </p>
     *
     * @param userId the UUID of the user.
     * @return a list of valid training DTOs.
     */
    List<UserTrainingDto> getValidTrainingsForUser(UUID userId);

    /**
     * Marks a non-quiz module (e.g., VIDEO, PDF) as completed.
     * <p>
     * Moves the user's progress pointer to the next module in the sequence.
     * </p>
     *
     * @param userEmail  the email of the user.
     * @param trainingId the UUID of the training.
     * @param moduleId   the UUID of the module being completed.
     * @throws IllegalArgumentException if the user tries to skip modules or the
     *                                  module is a Quiz.
     */
    void completeModule(String userEmail, UUID trainingId, UUID moduleId);

    /**
     * Processes a quiz submission.
     * <p>
     * Calculates the score based on provided answers and compares it against the
     * passing threshold.
     * If passed, moves to the next module. If failed, sets status to FAILED.
     * </p>
     *
     * @param userEmail  the email of the user.
     * @param trainingId the UUID of the training.
     * @param moduleId   the UUID of the quiz module.
     * @param submission the DTO containing the user's selected answers.
     * @return {@code true} if the user passed the quiz, {@code false} otherwise.
     */
    QuizResultDto submitQuiz(String userEmail, UUID trainingId, UUID moduleId, QuizSubmissionDto submission);

    /**
     * Administratively resets a user's training progress.
     * <p>
     * Reverts the user to the start of the training (NOT_STARTED), clearing all
     * progress,
     * history, and quiz answers. Used for forcing a retake or fixing issues.
     * </p>
     *
     * @param userId     the UUID of the user.
     * @param trainingId the UUID of the training.
     */
    void resetUserTrainingProgress(UUID userId, UUID trainingId);

    /**
     * Forces all users who have completed the training to retake it.
     * <p>
     * Typically called when the training definition (content) is significantly
     * updated.
     * </p>
     *
     * @param training the training entity that was updated.
     */
    void resetProgressForTraining(Training training);

    /**
     * Forces users to retake a specific module (and possibly subsequent ones).
     * <p>
     * Called when a specific module within a training is updated.
     * </p>
     *
     * @param module the module entity that was updated.
     * @param mode   the reset strategy (CURRENT_ONLY or CASCADE).
     */
    void resetProgressForModule(TrainingModule module, ResetMode mode);

    /**
     * Bulk assigns all trainings required for a specific security level.
     * <p>
     * Used to quickly onboard a user to a specific clearance level (e.g. "Assign
     * all Level 2 courses").
     * </p>
     *
     * @param userId the UUID of the user.
     * @param level  the target security level (1-4).
     */
    void assignTrainingsByLevelToUser(UUID userId, int level);

    /**
     * Retrieves detailed information about a training specifically for a given user
     * context.
     * <p>
     * Unlike the administrative view, this method personalises the response data
     * by:
     * <ul>
     * <li>Calculating the completion status ({@code completed}) for each module
     * based on the user's progress.</li>
     * <li>Determining the {@code currentModuleId} to allow the frontend to
     * implement a "Resume/Start" button.</li>
     * <li><b>Sanitising sensitive data:</b> It hides correct answers for quizzes to
     * prevent cheating.</li>
     * </ul>
     * </p>
     *
     * @param trainingId the UUID of the training definition.
     * @param userEmail  email of the authenticated user requesting the details.
     * @return a {@link TrainingResponseDto} containing the syllabus, module
     *         statuses, and resume point.
     * @throws RuntimeException if the user is not assigned to this training or the
     *                          record is missing.
     */
    UserTrainingDetailsDto getTrainingDetailsForUser(UUID trainingId, String userEmail);

    /**
     * Retrieves a paginated list of trainings assigned to the specified user, with
     * optional global filtering capabilities.
     * <p>
     * This method is primarily used to populate the "My Trainings" dashboard. It
     * supports server-side filtering,
     * allowing the frontend to request specific subsets of data (e.g., only "OHS"
     * trainings or only "Level 2" security trainings)
     * combined with pagination.
     * </p>
     *
     * @param userEmail     the email address of the authenticated user.
     * @param pageable      pagination information containing page number, size, and
     *                      sorting directives.
     * @param type          (Optional) the {@link TrainingType} to filter by. If
     *                      {@code null}, trainings of all types are returned.
     * @param securityLevel (Optional) the security level integer to filter by. If
     *                      {@code null}, trainings of all levels are returned.
     * @param status        (Optional) status of the training to filter by. If
     *                      {@code null}, trainings of all statuses are returned.
     * @return a {@link Page} of {@link UserTrainingDto} objects containing progress
     *         summaries (e.g., status, percentage) for the assigned trainings.
     * @throws RuntimeException if the user with the provided email cannot be found
     *                          in the database.
     */
    Page<UserTrainingDto> getUserTrainings(String userEmail, Pageable pageable, TrainingType type,
            Integer securityLevel, ProgressStatus status, String search);

    /**
     * Administratively assigns a specific training to all users who have a certain
     * security clearance level or higher.
     * <p>
     * This is useful for mandatory trainings that must be completed by all
     * personnel
     * of a given level.
     * </p>
     *
     * @param trainingId    the UUID of the training to assign.
     * @param securityLevel the target security level.
     */
    void assignTrainingToLevel(UUID trainingId, Integer securityLevel);

    /**
     * Retrieves a list of all users assigned to a specific training definition.
     *
     * @param trainingId the UUID of the training.
     * @return a list of assignment DTOs containing user details and status.
     */
    List<TrainingAssignmentDto> getTrainingAssignments(UUID trainingId);

    /**
     * Retrieves a summary of a specific training for a user, including progress
     * details.
     *
     * @param trainingId the UUID of the training.
     * @param userEmail  the email of the user.
     * @return a summary DTO.
     */
    TrainingSummaryDto getTrainingSummary(UUID trainingId, String userEmail);

    /**
     * Retrieves the quiz answers for a specific user and training module, allowing
     * review of their submission.
     *
     * @param trainingId the UUID of the training.
     * @param moduleId   the UUID of the module.
     * @param userEmail  the email of the user.
     * @return a list of quiz answer DTOs.
     */
    List<UserQuizAnswerDto> getQuizReview(UUID trainingId, UUID moduleId, String userEmail);
}