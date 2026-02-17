package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.TrainingModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object (DAO) for managing {@link TrainingModule} entities.
 * <p>
 * This repository includes custom query methods to navigate the sequential flow of a training
 * (finding the first module and determining the next module in line).
 * </p>
 */
@Repository
public interface TrainingModuleRepository extends JpaRepository<TrainingModule, UUID> {

    /**
     * Finds the next module in the sequence for a specific training.
     * <p>
     * It searches for the module with the lowest {@code orderIndex} that is greater than the
     * {@code currentOrderIndex}. This is used to determine what the user should see next
     * after completing the current step.
     * </p>
     *
     * @param trainingId        the UUID of the training context.
     * @param currentOrderIndex the index of the module the user just finished.
     * @return an {@link Optional} containing the next module, or empty if the user just finished the last module.
     */
    Optional<TrainingModule> findFirstByTrainingIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(UUID trainingId, int currentOrderIndex);

    /**
     * Finds the very first module of a training (the starting point).
     * <p>
     * Used when a user starts training for the first time or resets their progress.
     * It looks for the module with the lowest {@code orderIndex} (typically 0).
     * </p>
     *
     * @param trainingId the UUID of the training.
     * @return an {@link Optional} containing the first module, or empty if the training has no content.
     */
    Optional<TrainingModule> findFirstByTrainingIdOrderByOrderIndexAsc(UUID trainingId);
}