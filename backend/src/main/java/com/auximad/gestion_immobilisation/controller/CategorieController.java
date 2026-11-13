package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.CategorieDto;
import com.auximad.gestion_immobilisation.service.CategorieMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class CategorieController {

    private final CategorieMetier categorieMetier;

    @GetMapping
    public ResponseEntity<List<CategorieDto>> getAllCategories() {
        return ResponseEntity.ok(categorieMetier.getAllCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategorieDto> getCategorieById(@PathVariable Long id) {
        return ResponseEntity.ok(categorieMetier.getCategorieById(id));
    }

    @PostMapping
    public ResponseEntity<CategorieDto> createCategorie(@Valid @RequestBody CategorieDto dto) {
        return new ResponseEntity<>(categorieMetier.createCategorie(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieDto> updateCategorie(
            @PathVariable Long id,
            @Valid @RequestBody CategorieDto dto) {
        return ResponseEntity.ok(categorieMetier.updateCategorie(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategorie(@PathVariable Long id) {
        categorieMetier.deleteCategorie(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<CategorieDto>> searchCategories(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(categorieMetier.searchCategories(keyword));
    }
}