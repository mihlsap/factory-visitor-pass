package com.fvps.backend.services;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String savePhoto(MultipartFile file);
}