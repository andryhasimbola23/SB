package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.MouvementDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.MouvementMetier;
import com.auximad.gestion_immobilisation.service.PerimetreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mouvements")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class MouvementController {

    private final MouvementMetier mouvementMetier;
    private final PerimetreService perimetreService;

    @GetMapping
    public ResponseEntity<List<MouvementDto>> getAll(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(mouvementMetier
                .getAllMouvements(perimetreService.serviceIdDe(currentUser)));
    }

    @GetMapping("/immobilisation/{immobilisationId}")
    public ResponseEntity<List<MouvementDto>> getByImmobilisation(
            @PathVariable Long immobilisationId,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(mouvementMetier.getMouvementsByImmobilisation(
                immobilisationId, perimetreService.serviceIdDe(currentUser)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MouvementDto> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(mouvementMetier.getMouvementById(id, currentUser));
    }

    @PostMapping
    public ResponseEntity<MouvementDto> create(
            @Valid @RequestBody MouvementDto dto,
            @AuthenticationPrincipal Utilisateur currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        return new ResponseEntity<>(mouvementMetier.createMouvement(dto, userId), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mouvementMetier.deleteMouvement(id);
        return ResponseEntity.noContent().build();
    }
}