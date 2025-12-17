package com.fvps.backend.services.impl;

import com.fvps.backend.services.AuditLogService;
import com.fvps.backend.services.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;
    private final AuditLogService auditLogService;

    public FileStorageServiceImpl(@Value("${file.upload-dir}") String uploadDir, AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.fileStorageLocation = Paths.get(uploadDir)
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored: " + this.fileStorageLocation, ex);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Security Validation:</b> Performs a strict check to ensure the file is a valid image.
     * It uses {@link ImageIO#read} to parse the stream; if this fails, the file is rejected even if the extension is correct.
     * This prevents malicious uploads (e.g. executables disguised as images).</li>
     * <li><b>Sanitisation:</b> The original filename is discarded. A new {@link UUID} is generated
     * to prevent filename collisions and path traversal attacks.</li>
     * </ul>
     * </p>
     */
    @Override
    public String savePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }

        try (InputStream is = file.getInputStream()) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) {
                throw new IllegalArgumentException("The uploaded file is damaged or is not a valid image.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error verifying file content.", e);
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.lastIndexOf(".") > 0) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String newFilename = UUID.randomUUID() + extension;
            Path targetLocation = this.fileStorageLocation.resolve(newFilename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            return newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Could not store file. Error: " + e.getMessage());
        }
    }

    @Override
    public Resource loadPhoto(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File path error: " + filename, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * This method is designed to be "safe" regarding exceptions. If the file deletion fails
     * (e.g. file locked, permissions error) or the file is missing, it does <b>not</b> throw an exception
     * to the caller. Instead, it logs the failure to the {@link AuditLogService}.
     * This ensures that auxiliary clean-up tasks do not crash the main business transaction.
     * </p>
     */
    @Override
    public void deletePhoto(String filename) {
        if (filename == null) return;

        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            boolean deleted = Files.deleteIfExists(filePath);

            if (!deleted) {
                auditLogService.logEvent("FILE_CLEANUP_WARNING", "File to delete not found: " + filename);
            }

        } catch (IOException e) {
            auditLogService.logEvent("FILE_DELETE_ERROR",
                    "Failed to delete old photo: " + filename + ". Error: " + e.getMessage());
        }
    }
}