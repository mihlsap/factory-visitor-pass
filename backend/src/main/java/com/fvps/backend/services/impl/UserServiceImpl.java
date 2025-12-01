package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.User;
import com.fvps.backend.repositories.UserRepository;
import com.fvps.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @Override
    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
    }
}
