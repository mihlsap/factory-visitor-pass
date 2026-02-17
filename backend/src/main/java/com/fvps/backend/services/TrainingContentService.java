package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.*;
import com.fvps.backend.domain.entities.Training;
import com.fvps.backend.domain.enums.TrainingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for the Content Management (CMS) aspect of trainings.
 * <p>
 * This service allows administrators to define the structure and content of
 * courses.
 * It handles the hierarchy: <b>Training -> Modules -> Questions</b>.
 * Unlike {@link TrainingProgressService}, which handles user interaction, this
 * service
 * focuses on the static definitions of the educational material.
 * </p>
 */
public interface TrainingContentService {

    // --- Training Level Operations ---

    /**
     * Creates a new training definition.
     *
     * @param request the initial data for the training.
     * @return the created training details.
     */
    TrainingResponseDto createTraining(CreateTrainingRequest request);

    /**
     * Updates an existing training.
     * <p>
     * <b>Warning:</b> Changing parameters like {@code validityPeriodDays} may
     * trigger
     * a retroactive update of validity dates for users who have already completed
     * this training.
     * </p>
     *
     * @param id      the UUID of the training to update.
     * @param request the new data.
     * @return the updated training details.
     * @throws org.springframework.dao.OptimisticLockingFailureException if the
     *                                                                   version
     *                                                                   does not
     *                                                                   match.
     */
    TrainingResponseDto updateTraining(UUID id, CreateTrainingRequest request);

    /**
     * Permanently deletes training and all associated user progress records.
     *
     * @param id the UUID of the training.
     */
    void deleteTraining(UUID id);

    /**
     * Retrieves full details of the training, including all modules and questions.
     *
     * @param id the UUID of the training.
     * @return the full DTO structure.
     */
    TrainingResponseDto getTrainingDetails(UUID id);

    /**
     * Retrieves the raw entity.
     * <p>
     * Mostly used internally or for mapping purposes.
     * </p>
     *
     * @param id the UUID of the training.
     * @return the entity object.
     */
    Training getTrainingById(UUID id);

    /**
     * Retrieves a paginated list of training summaries with optional filters.
     * <p>
     * This method is optimized for list views (e.g., admin dashboard), returning
     * lightweight DTOs
     * instead of full training details.
     * </p>
     *
     * @param type     filter by training type (e.g., OHS).
     * @param level    filter by security clearance level.
     * @param search   search usage in title.
     * @param pageable pagination info.
     * @return a page of training summaries.
     */
    Page<TrainingSummaryDto> getAllTrainings(TrainingType type, Integer level, String search, Pageable pageable);

    // --- Module Level Operations ---

    /**
     * Adds a new content module (slide, video, or quiz) to a training.
     *
     * @param trainingId the parent training ID.
     * @param request    the module definition.
     * @return the updated training DTO.
     */
    TrainingResponseDto addModuleToTraining(UUID trainingId, CreateModuleRequest request);

    /**
     * Updates a module's content or position in the sequence.
     *
     * @param moduleId the UUID of the module.
     * @param request  the new data.
     * @return the updated parent training DTO.
     */
    TrainingResponseDto updateModule(UUID moduleId, UpdateModuleRequest request);

    /**
     * Removes a module from a training.
     *
     * @param moduleId the UUID of the module to delete.
     * @return the updated parent training DTO.
     */
    TrainingResponseDto deleteModule(UUID moduleId);

    /**
     * Retrieves details of a specific module.
     *
     * @param moduleId the UUID of the module.
     * @return the module DTO.
     */
    ModuleDto getModule(UUID moduleId);

    void reorderModules(UUID trainingId, List<UUID> moduleIds);

    // --- Question Level Operations ---

    /**
     * Adds a question to a specific quiz module.
     *
     * @param moduleId the UUID of the quiz module.
     * @param request  the question definition.
     * @return the updated parent training DTO.
     */
    TrainingResponseDto addQuestionToModule(UUID moduleId, CreateQuestionRequest request);

    /**
     * Updates an existing question.
     *
     * @param questionId the UUID of the question.
     * @param request    the new question data.
     * @return the updated parent training DTO.
     */
    TrainingResponseDto updateQuestion(UUID questionId, UpdateQuestionRequest request);

    /**
     * Removes a question from a quiz.
     *
     * @param questionId the UUID of the question.
     * @return the updated parent training DTO.
     */
    TrainingResponseDto deleteQuestion(UUID questionId);

    /**
     * Retrieves details of a specific question.
     *
     * @param questionId the UUID of the question.
     * @return the question DTO.
     */
    QuestionDto getQuestion(UUID questionId);
}