package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data Access Object (DAO) for managing {@link QuizQuestion} entities.
 * <p>
 * This interface provides standard CRUD operations for questions belonging to quiz modules.
 * It allows adding, updating, and removing questions that are part of a specific training module.
 * </p>
 */
@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
}