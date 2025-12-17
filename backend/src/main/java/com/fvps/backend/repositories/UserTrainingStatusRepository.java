package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.UserTrainingStatus;
import com.fvps.backend.domain.enums.ProgressStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object (DAO) for managing {@link UserTrainingStatus} persistence.
 * <p>
 * This repository handles the intersection between Users and Trainings. It is heavily used
 * for tracking course progress, retrieving assigned trainings for dashboard views,
 * and monitoring pass validity expiration.
 * </p>
 */
@Repository
public interface UserTrainingStatusRepository extends JpaRepository<UserTrainingStatus, UUID> {

    /**
     * Retrieves a paginated list of training statuses for a specific user.
     * <p>
     * <b>Performance Note:</b> Uses {@code @EntityGraph} to eagerly fetch the associated
     * {@code training} and {@code currentModule} entities in a single query.
     * This avoids the "N+1 select problem" when rendering the user's dashboard.
     * </p>
     *
     * @param userId   the UUID of the user.
     * @param pageable pagination information.
     * @return a page of training statuses populated with training details.
     */
    @EntityGraph(attributePaths = {"training", "currentModule"})
    Page<UserTrainingStatus> findByUserId(UUID userId, Pageable pageable);

    /**
     * Retrieves all training statuses for a specific user (non-paginated).
     * <p>
     * Similar to the paginated version, this method eagerly loads associated entities.
     * Useful for internal logic where all records are needed at once.
     * </p>
     *
     * @param userId the UUID of the user.
     * @return a list of training statuses populated with training details.
     */
    @EntityGraph(attributePaths = {"training", "currentModule"})
    List<UserTrainingStatus> findByUserId(UUID userId);

    /**
     * Finds all status records associated with a specific training definition.
     * <p>
     * Used for reporting (e.g. "Show me all users currently assigned to Fire Safety").
     * </p>
     *
     * @param trainingId the UUID of the training.
     * @return a list of status records.
     */
    List<UserTrainingStatus> findAllByTrainingId(UUID trainingId);

    /**
     * Checks if a user is already assigned to a specific training.
     * <p>
     * Used to prevent duplicate assignments.
     * </p>
     *
     * @param userId     the UUID of the user.
     * @param trainingId the UUID of the training.
     * @return true if an assignment exists, false otherwise.
     */
    boolean existsByUserIdAndTrainingId(UUID userId, UUID trainingId);

    /**
     * Removes all progress records for a specific training.
     * <p>
     * Typically called when a Training definition is deleted from the system, ensuring
     * no orphaned progress records remain.
     * </p>
     *
     * @param trainingId the UUID of the training to clean up.
     */
    void deleteByTrainingId(UUID trainingId);

    /**
     * Retrieves the specific progress record for a user in a specific training.
     * <p>
     * Used when a user enters a training to determine where they left off (current module)
     * or to update their progress.
     * </p>
     *
     * @param userId     the UUID of the user.
     * @param trainingId the UUID of the training.
     * @return an {@link Optional} containing the status if found.
     */
    Optional<UserTrainingStatus> findByUserIdAndTrainingId(UUID userId, UUID trainingId);

    /**
     * Finds records based on status and validity expiration date range.
     * <p>
     * <b>Use Case:</b> This method is primarily used by background scheduler jobs (Cron) to find
     * users whose certificates are about to expire (or have just expired) in order to send
     * automated email notifications.
     * </p>
     * <p>
     * Eagerly fetches {@code user} and {@code training} to facilitate email generation without
     * extra queries.
     * </p>
     *
     * @param status the progress status (usually COMPLETED).
     * @param start  the start of the time range.
     * @param end    the end of the time range.
     * @return a list of matching records with populated User and Training entities.
     */
    @EntityGraph(attributePaths = {"user", "training"})
    List<UserTrainingStatus> findAllByStatusAndValidUntilBetween(
            ProgressStatus status,
            LocalDateTime start,
            LocalDateTime end
    );
}