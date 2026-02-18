package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.TrainingSummaryDto;
import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PdfGeneratorServiceImplTest {

    private PdfGeneratorServiceImpl pdfGeneratorService;

    @TempDir
    Path tempDir;

    @Mock
    private MessageSource messageSource;

    @Mock
    private Locale defaultLocale;

    private final Clock clock = Clock.systemDefaultZone();

    @BeforeEach
    void setUp() {
        pdfGeneratorService = new PdfGeneratorServiceImpl(clock, messageSource, defaultLocale);
        ReflectionTestUtils.setField(pdfGeneratorService, "uploadDir", tempDir.toString());
    }

    @Test
    void shouldGeneratePdf_whenUserHasNoPhoto() throws IOException {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Jan").surname("Testowy")
                .email("jan@test.com")
                .companyName("Test Corp")
                .clearanceLevel(2)
                .photoUrl(null)
                .build();

        UserTrainingDto trainingDto = UserTrainingDto.builder()
                .training(TrainingSummaryDto.builder().title("BHP Training").build())
                .validUntil(LocalDateTime.now(clock).plusDays(30))
                .build();

        byte[] pdfBytes = pdfGeneratorService.generatePassPdf(user, List.of(trainingDto));

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        PdfReader reader = new PdfReader(pdfBytes);
        assertTrue(reader.getNumberOfPages() > 0);
        reader.close();
    }

    @Test
    void shouldGeneratePdf_whenUserHasPhoto() throws IOException {
        String photoFilename = "avatar.jpg";
        Files.write(tempDir.resolve(photoFilename), new byte[]{1, 2, 3});

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Jan").surname("Z Obrazkiem")
                .email("jan@img.com")
                .photoUrl(photoFilename)
                .clearanceLevel(1)
                .build();

        byte[] pdfBytes = pdfGeneratorService.generatePassPdf(user, Collections.emptyList());

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}