package com.fvps.backend.services;

import java.util.UUID;

public interface UserClearanceService {
    void recalculateUserClearance(UUID userId);
}