package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.ProgressStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_training_status")
public class UserTrainingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgressStatus status;

    private int currentModuleIndex; // 0, 1, 2... (wskazuje, który moduł teraz robi użytkownik)

    private Double quizScore; // Wynik testu w % (np. 0.85)

    private LocalDateTime completedAt;

    private LocalDateTime validUntil; // Data ważności uprawnień

    private boolean isPassRevoked; // Czy admin unieważnił przepustkę ręcznie?

    // Metoda pomocnicza, ustawiana przed zapisem po raz pierwszy
    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = ProgressStatus.NOT_STARTED;
        }
        this.isPassRevoked = false;
        this.currentModuleIndex = 0;
    }
}