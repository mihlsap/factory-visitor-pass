package com.fvps.backend.domain.entities;

import com.fvps.backend.domain.enums.ModuleType;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private ModuleType type;

    @Column(nullable = false)
    private String contentUrl;

    @Column(nullable = false)
    private int order;
}
