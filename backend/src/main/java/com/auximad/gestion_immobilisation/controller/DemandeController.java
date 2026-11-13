package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.DemandeDecisionDto;
import com.auximad.gestion_immobilisation.dto.DemandeDto;
import com.auximad.gestion_immobilisation.dto.DemandeStatsDto;
import com.auximad.gestion_immobilisation.dto.ImmobilisationCandidatDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.DemandeMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Demandes d'immobilisation : dépôt par un employé, traitement par un ADMIN.
 *
 * Les règles d'accès ressorties au contournement : la décision et la suppression
 * sont réservées à l'ADMIN, la lecture est filtrée par identité côté métier —
 * le contrôleur ne se contente jamais d'une vérification de rôle pour l'accès
 * unitaire.
 */
@RestController
@RequestMapping("/api/demandes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class DemandeController {

    private final DemandeMetier demandeMetier;

    /** Liste (ADMIN : toutes ; EMPLOYE : les siennes). Filtres facultatifs. */
    @GetMapping
    public ResponseEntity<List<DemandeDto>> getAll(
            @AuthenticationPrincipal Utilisateur currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String statut) {
        return ResponseEntity.ok(demandeMetier.getAll(currentUser, keyword, statut));
    }

    /**
     * Compteurs — déclaré avant l'accès unitaire pour éviter que « stats » ne
     * soit tenté d'être converti en identifiant numérique.
     */
    @GetMapping("/stats")
    public ResponseEntity<DemandeStatsDto> getStats(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(demandeMetier.getStats(currentUser));
    }

    /**
     * Biens livrables sur une demande (ADMIN).
     *
     * Déclaré avant l'accès unitaire, au même titre que `/stats`, pour que
     * « candidats » ne soit jamais tenté d'être lu comme un identifiant.
     * La sélection n'est qu'une intention : `decider` revalide le bien au moment
     * de la décision, cette liste peut donc avoir pris de l'âge.
     */
    @GetMapping("/candidats")
    public ResponseEntity<List<ImmobilisationCandidatDto>> getCandidats(
            @AuthenticationPrincipal Utilisateur currentUser,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(demandeMetier.getCandidats(categorieId, q, currentUser));
    }

    /** Accès unitaire : 404 si la demande appartient à quelqu'un d'autre. */
    @GetMapping("/{id}")
    public ResponseEntity<DemandeDto> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(demandeMetier.getById(id, currentUser));
    }

    @PostMapping
    public ResponseEntity<DemandeDto> create(
            @Valid @RequestBody DemandeDto dto,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return new ResponseEntity<>(demandeMetier.create(dto, currentUser), HttpStatus.CREATED);
    }

    /** Décision — isolée dans un sous-chemin PATCH pour restreindre le rôle au backend. */
    @PatchMapping("/{id}/decision")
    public ResponseEntity<DemandeDto> decider(
            @PathVariable Long id,
            @Valid @RequestBody DemandeDecisionDto dto,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(demandeMetier.decider(id, dto, currentUser));
    }

    /** Retrait de sa propre demande, avant traitement. */
    @PatchMapping("/{id}/annuler")
    public ResponseEntity<DemandeDto> annuler(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(demandeMetier.annuler(id, currentUser));
    }
}
