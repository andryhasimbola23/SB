package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.NotificationDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.NotificationMetier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Notifications in-app du connecté — chaque route est scopée au porteur du
 * jeton : aucun accès transverse à la boîte d'autrui n'est possible.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private final NotificationMetier notificationMetier;

    /** Liste complète (la cloche affiche les 10 plus récentes côté client). */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> mesNotifications(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(notificationMetier.mesNotifications(currentUser.getId()));
    }

    /** Compteur du badge. */
    @GetMapping("/non-lues")
    public ResponseEntity<Map<String, Long>> nonLues(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(Map.of(
                "count", notificationMetier.compterNonLues(currentUser.getId())));
    }

    @PutMapping("/{id}/lue")
    public ResponseEntity<NotificationDto> marquerLue(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(notificationMetier.marquerLue(id, currentUser.getId()));
    }

    /** « Tout marquer comme lu » — 204, le corps porte le nombre si besoin. */
    @PutMapping("/toutes-lues")
    public ResponseEntity<Map<String, Integer>> toutMarquerLue(
            @AuthenticationPrincipal Utilisateur currentUser) {
        return ResponseEntity.ok(Map.of(
                "marquees", notificationMetier.toutMarquerLue(currentUser.getId())));
    }
}
