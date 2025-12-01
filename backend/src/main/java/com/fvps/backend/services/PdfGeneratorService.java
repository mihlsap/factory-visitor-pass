package com.fvps.backend.services;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;
import java.util.List;

public interface PdfGeneratorService {
    byte[] generatePassPdf(User user, List<UserTrainingDto> validTrainings);
}