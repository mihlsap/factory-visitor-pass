package com.fvps.backend.services;

import com.fvps.backend.domain.enums.UserStatus;

import java.util.UUID;

public interface AdminService {

    void changeUserStatus(UUID userId, UserStatus status);

    byte[] generatePassPdf(UUID userId);
}