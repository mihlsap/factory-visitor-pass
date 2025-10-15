package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.UserRole;
import com.fvps.backend.domain.enums.UserStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String surname;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String companyName;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_course",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private List<Course> userCourses;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProgress> progressList = new ArrayList<>();

    @Column(nullable = false)
    private String photoUrl;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private LocalDateTime lastLogin;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @PrePersist
    protected  void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected  void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
