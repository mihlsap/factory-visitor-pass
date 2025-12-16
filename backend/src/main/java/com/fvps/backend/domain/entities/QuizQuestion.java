package com.fvps.backend.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single question within a Quiz module.
 * <p>
 * This entity stores the question text, the list of possible answers, and the index
 * of the correct answer. It relates to a specific {@link TrainingModule} of type QUIZ.
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    /**
     * Unique identifier for the question (Primary Key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The content of the question displayed to the user.
     * <p>
     * Example: "What should you do in case of a fire alarm?"
     * </p>
     */
    @Column(nullable = false)
    private String questionText;

    /**
     * Determines the sequence of the question within the quiz.
     */
    private int orderIndex;

    /**
     * A list of possible answer choices.
     * <p>
     * Stored in a separate table {@code quiz_question_options}.
     * Fetched eagerly so that options are always available when the question is loaded.
     * </p>
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text")
    private List<String> options;

    /**
     * The 0-based index of the correct answer within the {@link #options} list.
     * <p>
     * <b>Security Note:</b> This field is marked with {@code @JsonIgnore} to prevent it
     * from being serialized and sent to the frontend. This ensures users cannot inspect
     * the network traffic or page source to find the correct answer (anti-cheating measure).
     * </p>
     */
    @JsonIgnore
    private int correctOptionIndex;

    /**
     * The parent module (Quiz) this question belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    @ToString.Exclude
    @JsonIgnore
    private TrainingModule module;

    /**
     * Optimistic locking version.
     * <p>
     * Prevents lost updates when administrators modify the question concurrently.
     * </p>
     */
    @Version
    private Long version;

    /**
     * Checks equality based on the entity identifier (ID).
     * <p>
     * Implemented to safely handle Hibernate proxies.
     * </p>
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        QuizQuestion that = (QuizQuestion) o;
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