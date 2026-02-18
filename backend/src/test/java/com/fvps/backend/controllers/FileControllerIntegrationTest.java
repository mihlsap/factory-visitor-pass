package com.fvps.backend.controllers;

import com.fvps.backend.services.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

    @Test
    @WithMockUser
    void shouldServePhoto_whenExists() throws Exception {
        String filename = "avatar.jpg";
        Resource mockResource = new ByteArrayResource(new byte[] { 1, 2, 3 });

        when(fileStorageService.loadPhoto(filename)).thenReturn(mockResource);

        mockMvc.perform(get("/api/uploads/photos/" + filename))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[] { 1, 2, 3 }));
    }

    @Test
    @WithMockUser
    void shouldReturn404_whenPhotoNotFound() throws Exception {
        String filename = "missing.jpg";
        when(fileStorageService.loadPhoto(filename)).thenThrow(new RuntimeException("File not found"));

        mockMvc.perform(get("/api/uploads/photos/" + filename))
                .andExpect(status().isBadRequest());
    }
}