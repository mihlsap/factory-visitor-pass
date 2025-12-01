package com.fvps.backend.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fvps.backend.domain.enums.ModuleType;
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
@Table(name = "training_modules")
public class TrainingModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;

    private int orderIndex; // 1, 2, 3...

    @Enumerated(EnumType.STRING)
    private ModuleType type;

    private String contentUrl; // Link do pliku (jeśli VIDEO/PDF)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    @ToString.Exclude // Unikamy pętli w toString()
    @JsonIgnore
    private Training training;

    // Pytania (tylko jeśli type == QUIZ)
    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuizQuestion> questions = new ArrayList<>();
}