package com.fvps.backend.services.impl;

import com.fvps.backend.services.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceImplTest {

    @Mock
    private AuditLogService auditLogService;

    private FileStorageServiceImpl fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageServiceImpl(tempDir.toString(), auditLogService);
    }

    @Test
    void shouldSaveValidImage_Successfully() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile(
                "photo", "test.jpg", "image/jpeg", imageBytes
        );

        String filename = fileStorageService.savePhoto(file);

        assertNotNull(filename);
        assertTrue(filename.endsWith(".jpg"));
        assertTrue(Files.exists(tempDir.resolve(filename)));
    }

    @Test
    void shouldThrowException_whenFileIsNotImage() {
        MockMultipartFile fakeFile = new MockMultipartFile(
                "photo", "virus.exe.jpg", "image/jpeg", "To nie jest obrazek".getBytes()
        );

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.savePhoto(fakeFile));

        assertTrue(ex.getMessage().contains("not a valid image"));
    }

    @Test
    void shouldThrowException_whenMimeTypeIsInvalid() {
        MockMultipartFile badTypeFile = new MockMultipartFile(
                "doc", "cv.pdf", "application/pdf", new byte[10]
        );

        assertThrows(IllegalArgumentException.class, () -> fileStorageService.savePhoto(badTypeFile));
    }

    @Test
    void shouldLoadPhoto_Successfully() throws IOException {
        String filename = "existing.jpg";
        Path filePath = tempDir.resolve(filename);
        Files.writeString(filePath, "dummy content");

        Resource resource = fileStorageService.loadPhoto(filename);

        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void shouldDeletePhoto_andLogWarning_ifNotFound() {
        String nonExistentFile = "ghost.jpg";

        fileStorageService.deletePhoto(nonExistentFile);

        verify(auditLogService).logEvent(eq("FILE_CLEANUP_WARNING"), anyString());
    }
}