package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/photo")
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {

        String url = fileStorageService.storeUserPhoto(file, userId != null ? userId : 0L);

        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/immobilisation-photo")
    public ResponseEntity<Map<String, String>> uploadImmobilisationPhoto(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.storeImmobilisationPhoto(file);
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return ResponseEntity.ok(response);
    }
}