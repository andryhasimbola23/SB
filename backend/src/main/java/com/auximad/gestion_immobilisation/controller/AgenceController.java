package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.AgenceDto;
import com.auximad.gestion_immobilisation.service.AgenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agences")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AgenceController {

    private final AgenceService agenceService;

    /**
     * GET /api/agences - Liste toutes les agences
     */
    @GetMapping
    public ResponseEntity<List<AgenceDto>> getAllAgences() {
        return ResponseEntity.ok(agenceService.getAllAgences());
    }

    /**
     * GET /api/agences/{id} - Récupère une agence par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AgenceDto> getAgenceById(@PathVariable Long id) {
        return ResponseEntity.ok(agenceService.getAgenceById(id));
    }

    /**
     * POST /api/agences - Crée une nouvelle agence
     */
    @PostMapping
    public ResponseEntity<AgenceDto> createAgence(@Valid @RequestBody AgenceDto dto) {
        AgenceDto created = agenceService.createAgence(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * PUT /api/agences/{id} - Met à jour une agence
     */
    @PutMapping("/{id}")
    public ResponseEntity<AgenceDto> updateAgence(
            @PathVariable Long id,
            @Valid @RequestBody AgenceDto dto) {
        return ResponseEntity.ok(agenceService.updateAgence(id, dto));
    }

    /**
     * DELETE /api/agences/{id} - Supprime une agence
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgence(@PathVariable Long id) {
        agenceService.deleteAgence(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/agences/search?keyword=xxx - Recherche
     */
    @GetMapping("/search")
    public ResponseEntity<List<AgenceDto>> searchAgences(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(agenceService.searchAgences(keyword));
    }
}