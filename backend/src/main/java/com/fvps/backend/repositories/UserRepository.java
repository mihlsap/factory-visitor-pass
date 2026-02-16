package com.fvps.backend.repositories;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object (DAO) for managing {@link User} persistence.
 * <p>
 * This repository handles all database operations related to user accounts,
 * including registration, profile updates, and fetching credentials for
 * authentication.
 * </p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

        /**
         * Retrieves a user entity based on their email address.
         * <p>
         * Since the application uses the email address as the unique login identifier
         * (username),
         * this method is essential for the authentication process (loading
         * {@code UserDetails}
         * for Spring Security).
         * </p>
         *
         * @param email the email address to search for.
         * @return an {@link Optional} containing the user if found, or empty if no user
         *         exists with that email.
         */
        Optional<User> findByEmail(String email);

        /**
         * Finds users with a clearance level greater than or equal to the specified
         * level.
         *
         * @param level the minimum clearance level.
         * @return a list of users meeting the criteria.
         */
        List<User> findByClearanceLevelGreaterThanEqual(Integer level);

        /**
         * Retrieves a paginated list of users based on dynamic filters.
         * <p>
         * Used in the admin user management panel. Supports search by name/email
         * and filtering by role, status, or clearance level.
         * </p>
         *
         * @param search   partial match for name, surname, or email.
         * @param role     filter by user role (e.g., ADMIN).
         * @param status   filter by account status (e.g., ACTIVE).
         * @param level    filter by exact clearance level.
         * @param pageable pagination info.
         * @return a filtered page of users.
         */
        @Query("SELECT u FROM User u WHERE " +
                        "(:search IS NULL OR :search = '' OR " +
                        "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(u.surname) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
                        "(:role IS NULL OR u.role = :role) AND " +
                        "(:status IS NULL OR u.status = :status) AND " +
                        "(:level IS NULL OR u.clearanceLevel = :level)")
        Page<User> findUsersWithFilters(
                        @Param("search") String search,
                        @Param("role") UserRole role,
                        @Param("status") UserStatus status,
                        @Param("level") Integer level,
                        Pageable pageable);
}