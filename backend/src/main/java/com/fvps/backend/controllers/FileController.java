package com.fvps.backend.controllers;

import com.fvps.backend.services.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "Endpoints for serving uploaded files (e.g., user photos).")
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "Get User Photo", description = "Retrieves a user profile photo by its filename. Served as an image/jpeg resource.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Photo retrieved successfully",
                    content = @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/photos/{filename}")
    public ResponseEntity<Resource> getPhoto(
            @Parameter(description = "Filename of the photo (e.g., user-uuid.jpg)", required = true, example = "user-550e8400-e29b-41d4-a716-446655440000.jpg")
            @PathVariable String filename
    ) {
        Resource resource = fileStorageService.loadPhoto(filename);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource);
    }
}