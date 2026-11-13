package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.FournisseurDto;
import com.auximad.gestion_immobilisation.service.FournisseurMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fournisseurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class FournisseurController {

    private final FournisseurMetier fournisseurMetier;

    @GetMapping
    public ResponseEntity<List<FournisseurDto>> getAllFournisseurs() {
        return ResponseEntity.ok(fournisseurMetier.getAllFournisseurs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FournisseurDto> getFournisseurById(@PathVariable Long id) {
        return ResponseEntity.ok(fournisseurMetier.getFournisseurById(id));
    }

    @PostMapping
    public ResponseEntity<FournisseurDto> createFournisseur(@Valid @RequestBody FournisseurDto dto) {
        return new ResponseEntity<>(fournisseurMetier.createFournisseur(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FournisseurDto> updateFournisseur(
            @PathVariable Long id,
            @Valid @RequestBody FournisseurDto dto) {
        return ResponseEntity.ok(fournisseurMetier.updateFournisseur(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFournisseur(@PathVariable Long id) {
        fournisseurMetier.deleteFournisseur(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<FournisseurDto>> searchFournisseurs(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(fournisseurMetier.searchFournisseurs(keyword));
    }
}