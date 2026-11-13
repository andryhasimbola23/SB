package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.CampagneInventaireDto;
import com.auximad.gestion_immobilisation.dto.LigneInventaireDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.InventaireMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventaires")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class InventaireController {

    private final InventaireMetier inventaireMetier;

    // ============ CAMPAGNES ============

    @GetMapping("/campagnes")
    public ResponseEntity<List<CampagneInventaireDto>> getAllCampagnes() {
        return ResponseEntity.ok(inventaireMetier.getAllCampagnes());
    }

    @GetMapping("/campagnes/{id}")
    public ResponseEntity<CampagneInventaireDto> getCampagneById(@PathVariable Long id) {
        return ResponseEntity.ok(inventaireMetier.getCampagneById(id));
    }

    @PostMapping("/campagnes")
    public ResponseEntity<CampagneInventaireDto> createCampagne(
            @Valid @RequestBody CampagneInventaireDto dto,
            @AuthenticationPrincipal Utilisateur currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        return new ResponseEntity<>(inventaireMetier.createCampagne(dto, userId), HttpStatus.CREATED);
    }

    @PutMapping("/campagnes/{id}")
    public ResponseEntity<CampagneInventaireDto> updateCampagne(
            @PathVariable Long id,
            @Valid @RequestBody CampagneInventaireDto dto) {
        return ResponseEntity.ok(inventaireMetier.updateCampagne(id, dto));
    }

    @PostMapping("/campagnes/{id}/cloturer")
    public ResponseEntity<CampagneInventaireDto> cloturerCampagne(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(inventaireMetier.cloturerCampagne(id, currentUser.getId()));
    }

    @DeleteMapping("/campagnes/{id}")
    public ResponseEntity<Void> deleteCampagne(@PathVariable Long id) {
        inventaireMetier.deleteCampagne(id);
        return ResponseEntity.noContent().build();
    }

    // ============ LIGNES ============

    @GetMapping("/campagnes/{campagneId}/lignes")
    public ResponseEntity<List<LigneInventaireDto>> getLignesByCampagne(@PathVariable Long campagneId) {
        return ResponseEntity.ok(inventaireMetier.getLignesByCampagne(campagneId));
    }

    @PostMapping("/lignes/scan")
    public ResponseEntity<LigneInventaireDto> scanner(@Valid @RequestBody LigneInventaireDto dto) {
        return new ResponseEntity<>(inventaireMetier.scannerImmobilisation(dto), HttpStatus.CREATED);
    }

    @DeleteMapping("/lignes/{id}")
    public ResponseEntity<Void> deleteLigne(@PathVariable Long id) {
        inventaireMetier.deleteLigne(id);
        return ResponseEntity.noContent().build();
    }
}