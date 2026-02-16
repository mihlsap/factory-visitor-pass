package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.ProgressStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the progress and status of a specific User in a specific Training.
 * <p>
 * This entity acts as a join table between {@link User} and {@link Training},
 * but carries
 * additional state information. It tracks whether the user has started the
 * training,
 * which module they are currently on, their quiz score, and the validity period
 * of the completion.
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_training_status")
public class UserTrainingStatus {

    /**
     * Unique identifier for this progress record (Primary Key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user assigned to the training.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    /**
     * The training definition being taken by the user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    @ToString.Exclude
    private Training training;

    /**
     * The current lifecycle status of the training (e.g. IN_PROGRESS, COMPLETED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgressStatus status;

    /**
     * A pointer to the specific module the user is currently working on.
     * <p>
     * Acts as a "bookmark" to resume the training from the correct place.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_module_id")
    @ToString.Exclude
    private TrainingModule currentModule;

    /**
     * Timestamp when the user successfully finished the training.
     */
    private LocalDateTime completedAt;

    /**
     * The expiration date of the training validity.
     * <p>
     * Calculated based on {@code completedAt} +
     * {@link Training#getValidityPeriodDays()}.
     * Used to determine if the user's pass is valid.
     * </p>
     */
    private LocalDateTime validUntil;

    @Column(name = "is_pass_revoked")
    private boolean isPassRevoked;

    @OneToMany(mappedBy = "userTrainingStatus", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<UserQuizAnswer> quizAnswers = new ArrayList<>();

    /**
     * Sets default values before persisting a new record.
     * <p>
     * Default status: NOT_STARTED.
     * </p>
     */
    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = ProgressStatus.NOT_STARTED;
        }
    }

    /**
     * Checks equality based on the entity identifier (ID).
     * <p>
     * Handles Hibernate proxies correctly.
     * </p>
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass)
            return false;
        UserTrainingStatus that = (UserTrainingStatus) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    /**
     * Returns the hash code based on the effective class type.
     */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}