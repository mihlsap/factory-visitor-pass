package com.fvps.backend.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fvps.backend.domain.enums.ModuleType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single content unit within a {@link Training}.
 * <p>
 * A module corresponds to a step in the training course, such as a video lecture,
 * a PDF document to read, or a quiz to solve. Modules are ordered sequentially
 * using {@link #orderIndex}.
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "training_modules")
public class TrainingModule {

    /**
     * Unique identifier for the module (Primary Key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The display title of the module.
     * <p>
     * Example: "Introduction to Fire Safety".
     * </p>
     */
    private String title;

    /**
     * Determines the sequence position of this module within the training.
     * <p>
     * Modules are sorted by this index in ascending order (0, 1, 2...).
     * </p>
     */
    private int orderIndex;

    /**
     * The type of content provided by this module.
     * <p>
     * Determines how the frontend renders the module (e.g., video player vs PDF viewer vs quiz form).
     * </p>
     */
    @Enumerated(EnumType.STRING)
    private ModuleType type;

    /**
     * The URL to the external resource (video link or file path).
     * <p>
     * Relevant primarily for {@code VIDEO} and {@code PDF} types.
     * For {@code QUIZ} modules, this field is typically null.
     * </p>
     */
    private String contentUrl;

    /**
     * The parent training this module belongs to.
     * <p>
     * Marked with {@code @JsonIgnore} to prevent infinite recursion during JSON serialization
     * (Bi-directional relationship).
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    @ToString.Exclude
    @JsonIgnore
    private Training training;

    /**
     * Optimistic locking version.
     */
    @Version
    private Long version;

    /**
     * A list of questions associated with this module.
     * <p>
     * Only populated if {@link #type} is {@code QUIZ}.
     * When the module is deleted, its questions are also removed (Orphan Removal).
     * </p>
     */
    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    @ToString.Exclude
    private List<QuizQuestion> questions = new ArrayList<>();

    /**
     * Checks equality based on the entity identifier (ID).
     * <p>
     * Safely handles Hibernate proxies to facilitate correct comparisons within persistence contexts.
     * </p>
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        TrainingModule that = (TrainingModule) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    /**
     * Returns the hash code based on the effective class type.
     */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}