package com.fvps.backend.services;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service responsible for physical file management operations.
 * <p>
 * This interface abstracts the underlying storage mechanism (local file system, cloud storage like S3, etc.)
 * allowing the application to save, retrieve, and remove user-uploaded content such as profile photos.
 * </p>
 */
public interface FileStorageService {

    /**
     * Persists an uploaded file to the storage system.
     * <p>
     * Handles the transfer of the file stream to the permanent storage location.
     * </p>
     *
     * @param file the multipart file uploaded by the user.
     * @return the unique generated filename used to reference the stored file, or {@code null} if the input was empty.
     * @throws RuntimeException if the file cannot be stored or is invalid.
     */
    String savePhoto(MultipartFile file);

    /**
     * Retrieves a file as a loadable resource.
     * <p>
     * Typically used to serve images directly to the frontend via HTTP.
     * </p>
     *
     * @param filename the unique name of the file to load.
     * @return a {@link Resource} handle to the file.
     * @throws RuntimeException if the file does not exist or is not readable.
     */
    Resource loadPhoto(String filename);

    /**
     * Removes a file from the storage system.
     * <p>
     * Used for cleanup when a user updates their profile photo or an account is deleted.
     * </p>
     *
     * @param filename the unique name of the file to delete.
     */
    void deletePhoto(String filename);
}