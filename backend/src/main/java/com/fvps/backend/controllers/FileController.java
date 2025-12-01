package com.fvps.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class FileController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @GetMapping("/photos/{filename}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG) // Lub PNG, przeglądarka sobie poradzi
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}