package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.DashboardStatsDto;
import com.auximad.gestion_immobilisation.dto.ImmobilisationDto;
import com.auximad.gestion_immobilisation.dto.MouvementDto;
import com.auximad.gestion_immobilisation.dto.RepartitionDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.DashboardMetier;
import com.auximad.gestion_immobilisation.service.PerimetreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    private final DashboardMetier dashboardMetier;
    private final PerimetreService perimetreService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(dashboardMetier
                .getStats(perimetreService.serviceIdDe(currentUser)));
    }

    @GetMapping("/repartition-categorie")
    public ResponseEntity<List<RepartitionDto>> getRepartitionCategorie(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(dashboardMetier
                .getRepartitionParCategorie(perimetreService.serviceIdDe(currentUser)));
    }

    @GetMapping("/repartition-service")
    public ResponseEntity<List<RepartitionDto>> getRepartitionService(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(dashboardMetier
                .getRepartitionParService(perimetreService.serviceIdDe(currentUser)));
    }

    /** Valeur du parc par agence — le graphique n'est affiché qu'à l'ADMIN. */
    @GetMapping("/repartition-agence")
    public ResponseEntity<List<RepartitionDto>> getRepartitionAgence(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(dashboardMetier
                .getRepartitionParAgence(perimetreService.serviceIdDe(currentUser)));
    }

    @GetMapping("/derniers-mouvements")
    public ResponseEntity<List<MouvementDto>> getDerniersMouvements(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(dashboardMetier
                .getDerniersMouvements(perimetreService.serviceIdDe(currentUser)));
    }

    @GetMapping("/dernieres-acquisitions")
    public ResponseEntity<List<ImmobilisationDto>> getDernieresAcquisitions(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(dashboardMetier
                .getDernieresAcquisitions(perimetreService.serviceIdDe(currentUser)));
    }
}
