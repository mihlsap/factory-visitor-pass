package com.fvps.backend.domain.enums;

public enum UserStatus {
    ACTIVE,   // Może wchodzić
    BLOCKED,  // Zakaz wstępu (Revoked)
    DELETED   // Konto usunięte (soft delete)
}