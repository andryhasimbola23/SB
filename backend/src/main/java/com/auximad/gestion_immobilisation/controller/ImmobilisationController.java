package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.ImmobilisationDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.ImmobilisationMetier;
import com.auximad.gestion_immobilisation.service.PerimetreService;
import com.auximad.gestion_immobilisation.dto.AmortissementPlanDto;
import com.auximad.gestion_immobilisation.service.AmortissementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/immobilisations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ImmobilisationController {

    private final ImmobilisationMetier immobilisationMetier;
    private final AmortissementService amortissementService;
    private final PerimetreService perimetreService;

    /** Lecture : limitée au périmètre de l'utilisateur courant. */
    @GetMapping
    public ResponseEntity<List<ImmobilisationDto>> getAll(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(immobilisationMetier
                .getAllImmobilisations(perimetreService.serviceIdDe(currentUser)));
    }

    /** Lecture unitaire : 404 si le bien est hors périmètre. */
    @GetMapping("/{id}")
    public ResponseEntity<ImmobilisationDto> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(immobilisationMetier.getImmobilisationById(id, currentUser));
    }

    /**
     * GET /api/immobilisations/code?code=90/26/SIT/2183/UCB/002
     * Identification d'un bien par son code unique — utilisé par le scanner
     * global (QR dont le contenu EST le code). Le code passe en query param
     * car il contient des « / » : en path, Tomcat rejeterait l'encodage %2F.
     * Même périmètre que la lecture par id : 404 si inconnu ou hors périmètre.
     */
    @GetMapping("/code")
    public ResponseEntity<ImmobilisationDto> getByCode(
            @RequestParam("code") String codeUnique,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(immobilisationMetier.getImmobilisationByCode(codeUnique, currentUser));
    }

    @PostMapping
    public ResponseEntity<ImmobilisationDto> create(
            @Valid @RequestBody ImmobilisationDto dto,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return new ResponseEntity<>(immobilisationMetier.createImmobilisation(dto, currentUser), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ImmobilisationDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ImmobilisationDto dto,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(immobilisationMetier.updateImmobilisation(id, dto, currentUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        immobilisationMetier.deleteImmobilisation(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ImmobilisationDto>> search(
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(immobilisationMetier
                .searchImmobilisations(keyword, perimetreService.serviceIdDe(currentUser)));
    }

    /**
     * POST /api/immobilisations/{id}/generer-qr
     * Génère un QR Code pour l'immobilisation.
     * Même périmètre d'accès que la consultation : un employé ne peut pas
     * générer le QR d'un bien d'un autre service.
     */
    @PostMapping("/{id}/generer-qr")
    public ResponseEntity<ImmobilisationDto> genererQrCode(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        immobilisationMetier.verifierAcces(id, currentUser);
        return ResponseEntity.ok(immobilisationMetier.genererQrCode(id));
    }

    /**
     * GET /api/immobilisations/{id}/amortissements
     * Récupère le plan d'amortissement
     */
    @GetMapping("/{id}/amortissements")
    public ResponseEntity<AmortissementPlanDto> getPlanAmortissement(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        immobilisationMetier.verifierAcces(id, currentUser);
        return ResponseEntity.ok(amortissementService.getPlanAmortissement(id));
    }

    /**
     * POST /api/immobilisations/{id}/calculer-amortissements
     * Calcule (ou recalcule) le plan d'amortissement
     */
    @PostMapping("/{id}/calculer-amortissements")
    public ResponseEntity<AmortissementPlanDto> calculerAmortissement(@PathVariable Long id) {
        return ResponseEntity.ok(amortissementService.calculerPlanAmortissement(id));
    }

    /**
     * GET /api/immobilisations/{id}/vnc
     * Récupère la VNC actuelle
     */
    @GetMapping("/{id}/vnc")
    public ResponseEntity<Map<String, Object>> getVnc(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        immobilisationMetier.verifierAcces(id, currentUser);
        BigDecimal vnc = amortissementService.calculerVncActuelle(id);
        Map<String, Object> response = new HashMap<>();
        response.put("vnc", vnc);
        return ResponseEntity.ok(response);
    }
}
