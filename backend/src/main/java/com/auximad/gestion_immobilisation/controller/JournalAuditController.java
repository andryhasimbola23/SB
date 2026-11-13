package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.ExportJournalDto;
import com.auximad.gestion_immobilisation.dto.JournalAuditDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.JournalAuditMetier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consultation du journal d'audit — route fermée à l'ADMIN dans SecurityConfig.
 *
 * Lecture seule : aucune écriture, mise à jour ou suppression n'est exposée.
 * Une trace d'audit se consulte, elle ne se corrige pas.
 */
@RestController
@RequestMapping("/api/journal-audit")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class JournalAuditController {

    private final JournalAuditMetier journalAuditMetier;

    /**
     * GET /api/journal-audit?action=&entite=&acteurId=
     * Filtres optionnels, cumulables ; sans paramètre, le journal complet
     * du plus récent au plus ancien.
     */
    @GetMapping
    public ResponseEntity<List<JournalAuditDto>> rechercher(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) Long acteurId) {
        return ResponseEntity.ok(journalAuditMetier.rechercher(action, entite, acteurId));
    }

    /**
     * POST /api/journal-audit/export — trace l'export CSV du journal.
     *
     * L'extraction du journal est une action sensible comme une autre : le
     * journal audite sa propre consultation. Route déjà fermée à l'ADMIN par
     * SecurityConfig ({@code /api/journal-audit/**}).
     */
    @PostMapping("/export")
    public ResponseEntity<Void> tracerExport(@AuthenticationPrincipal Utilisateur utilisateur,
                                             @RequestBody ExportJournalDto dto) {
        journalAuditMetier.enregistrerExport(utilisateur, dto);
        return ResponseEntity.noContent().build();
    }
}
