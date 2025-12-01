package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.Training;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TrainingRepository extends JpaRepository<Training, UUID> {
}