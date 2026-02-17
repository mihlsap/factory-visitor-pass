package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.Training;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data Access Object (DAO) for managing {@link Training} entities.
 * <p>
 * This interface provides standard CRUD operations for training definitions.
 * It acts as the primary data access point for creating, updating, and querying available courses.
 * </p>
 */
@Repository
public interface TrainingRepository extends JpaRepository<Training, UUID>, JpaSpecificationExecutor<Training> {

    /**
     * Retrieves a list of all trainings associated with a specific security level.
     * <p>
     * This method is typically used when a user needs to obtain a specific clearance level.
     * The system can fetch all required trainings for that level and assign them to the user en masse.
     * </p>
     *
     * @param securityLevel the security clearance level to filter by (e.g. 1, 2, 3).
     * @return a list of trainings matching the specified security level.
     */
    List<Training> findAllBySecurityLevel(int securityLevel);
}