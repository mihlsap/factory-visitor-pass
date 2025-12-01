package com.fvps.backend.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String questionText;

    @ElementCollection(fetch = FetchType.EAGER) // Prosta lista opcji (A, B, C)
    @CollectionTable(name = "quiz_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text")
    private List<String> options;

    private int correctOptionIndex; // 0, 1, 2...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    @ToString.Exclude
    @JsonIgnore
    private TrainingModule module;
}