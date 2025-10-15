package com.fvps.backend.domain.entities;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String answerText;

    @Column(nullable = false)
    private boolean isCorrect;
}
