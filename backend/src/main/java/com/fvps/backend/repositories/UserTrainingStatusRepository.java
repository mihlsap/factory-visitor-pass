package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.UserTrainingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserTrainingStatusRepository extends JpaRepository<UserTrainingStatus, UUID> {
    List<UserTrainingStatus> findByUserId(UUID userId);
}