package com.fvps.backend.services.impl;

import com.fvps.backend.services.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageServiceImpl(@Value("${file.upload-dir}") String uploadDir) {
        // Konwertujemy stringa na Path i robimy z niego ścieżkę absolutną
        // Niezależnie czy podasz "./uploads" czy "/Users/pablo/uploads", tu zrobi się porządek.
        this.fileStorageLocation = Paths.get(uploadDir)
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Nie można utworzyć katalogu na pliki w: " + this.fileStorageLocation, ex);
        }
    }

    @Override
    public String savePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            Path targetLocation = this.fileStorageLocation.resolve(newFilename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            return newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Błąd zapisu pliku: " + e.getMessage());
        }
    }
}