package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.TrainingType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trainings")
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private TrainingType type;

    private int validityPeriodDays; // np. 365

    @OneToMany(mappedBy = "training", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC") // Ważne: zawsze pobieraj moduły w dobrej kolejności
    @Builder.Default
    private List<TrainingModule> modules = new ArrayList<>();
}