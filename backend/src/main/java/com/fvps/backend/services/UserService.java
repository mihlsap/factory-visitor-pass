package com.fvps.backend.services;

import com.fvps.backend.domain.entities.User;

import java.util.List;
import java.util.UUID;

public interface UserService {
    List<User> getAll();
    User getById(UUID id);
}
