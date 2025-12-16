package com.fvps.backend.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fvps.backend.domain.enums.TrainingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a training definition in the system.
 * <p>
 * This entity acts as the root of the training structure aggregate. It defines the general properties
 * of a course (title, type, validity) and holds the list of sequential content {@link #modules}.
 * Completion of a training grants specific security clearance privileges based on {@link #securityLevel}.
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trainings")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Prevents serialization errors with lazy-loaded proxies
public class Training {

    /**
     * Unique identifier for the training (Primary Key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The display title of the training.
     * <p>
     * Example: "OHS Safety Level 1".
     * </p>
     */
    @Column(nullable = false)
    private String title;

    /**
     * A detailed description of the training's scope and purpose.
     * <p>
     * Stored as TEXT in the database to allow for longer content.
     * </p>
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Categorizes the training (e.g., OHS, FIRE_SAFETY, SECURITY).
     */
    @Enumerated(EnumType.STRING)
    private TrainingType type;

    /**
     * The number of days the certification remains valid after completion.
     * <p>
     * Used by the system scheduler to automatically expire user passes.
     * Example: 365 (1 year).
     * </p>
     */
    private int validityPeriodDays;

    /**
     * The minimum score ratio required to pass quizzes in this training.
     * <p>
     * Value ranges from 0.0 to 1.0 (e.g., 0.8 means 80%).
     * Default is 0.8.
     * </p>
     */
    @Builder.Default
    private Double passingThreshold = 0.8;

    /**
     * Optimistic locking version.
     * <p>
     * Ensures data integrity when multiple administrators attempt to edit the training simultaneously.
     * </p>
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * An ordered list of content modules (videos, slides, quizzes) that make up this training.
     * <p>
     * - {@code mappedBy = "training"}: The relationship is managed by the {@link TrainingModule} side.
     * - {@code cascade = CascadeType.ALL}: Operations (persist, remove) cascade to modules.
     * - {@code orphanRemoval = true}: Removing a module from this list deletes it from the database.
     * </p>
     */
    @OneToMany(mappedBy = "training", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    @ToString.Exclude
    private List<TrainingModule> modules = new ArrayList<>();

    /**
     * The security clearance level granted to the user upon completing this training.
     * <p>
     * Levels typically range from 1 (basic access) to higher numbers (restricted zones).
     * Default is 1.
     * </p>
     */
    @Column(nullable = false, columnDefinition = "integer default 1")
    private int securityLevel = 1;

    /**
     * Checks equality based on the entity identifier (ID).
     * <p>
     * This implementation handles Hibernate proxies correctly to ensure consistent behavior
     * across different persistence states.
     * </p>
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Training training = (Training) o;
        return getId() != null && Objects.equals(getId(), training.getId());
    }

    /**
     * Returns the hash code based on the effective class type.
     */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}