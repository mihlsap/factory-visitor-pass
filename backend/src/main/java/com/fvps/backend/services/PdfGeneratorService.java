package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;

import java.util.List;

/**
 * Service responsible for creating the printable Visitor Pass document.
 * <p>
 * This service encapsulates the document layout and formatting logic, generating a
 * portable document format (PDF) file that serves as physical proof of security clearance.
 * </p>
 */
public interface PdfGeneratorService {

    /**
     * Generates a PDF byte array representing the user's visitor pass.
     * <p>
     * The document includes the user's photo, personal details, security clearance level,
     * a verification QR code, and a list of valid training certifications.
     * </p>
     *
     * @param user           the user for whom the pass is generated.
     * @param validTrainings the list of active trainings to display on the pass.
     * @return a byte array containing the binary PDF data.
     * @throws RuntimeException if an error occurs during PDF construction (e.g. IO error).
     */
    byte[] generatePassPdf(User user, List<UserTrainingDto> validTrainings);
}