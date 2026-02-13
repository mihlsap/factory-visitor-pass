package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data Access Object (DAO) for managing {@link AuditLog} persistence.
 * <p>
 * This interface extends {@link JpaRepository} to provide standard CRUD
 * operations
 * (Create, Read, Update, Delete) and pagination capabilities for audit logs.
 * Since audit logs are immutable records, update operations should be used with
 * caution.
 * </p>
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}