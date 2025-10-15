package com.fvps.backend.domain.entities;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String question;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "quiz_answer",
            joinColumns = @JoinColumn(name = "quiz_id"),
            inverseJoinColumns = @JoinColumn(name = "quiz_answer_id")
    )
    private Set<QuizAnswer> answers = new HashSet<>();
}
