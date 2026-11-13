package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.ChangePasswordDto;
import com.auximad.gestion_immobilisation.dto.UtilisateurDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.UtilisateurMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class UtilisateurController {

    private final UtilisateurMetier utilisateurMetier;

    @GetMapping
    public ResponseEntity<List<UtilisateurDto>> getAllUtilisateurs() {
        return ResponseEntity.ok(utilisateurMetier.getAllUtilisateurs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UtilisateurDto> getUtilisateurById(@PathVariable Long id) {
        return ResponseEntity.ok(utilisateurMetier.getUtilisateurById(id));
    }

    @PostMapping
    public ResponseEntity<UtilisateurDto> createUtilisateur(@Valid @RequestBody UtilisateurDto dto) {
        return new ResponseEntity<>(utilisateurMetier.createUtilisateur(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UtilisateurDto> updateUtilisateur(
            @PathVariable Long id,
            @Valid @RequestBody UtilisateurDto dto) {
        return ResponseEntity.ok(utilisateurMetier.updateUtilisateur(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUtilisateur(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        utilisateurMetier.deleteUtilisateur(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordDto dto) {
        utilisateurMetier.changePassword(id, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UtilisateurDto>> searchUtilisateurs(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(utilisateurMetier.searchUtilisateurs(keyword));
    }
}