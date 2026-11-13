package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ExportJournalDto;
import com.auximad.gestion_immobilisation.dto.JournalAuditDto;
import com.auximad.gestion_immobilisation.model.JournalAudit;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.JournalAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Journal d'audit.
 *
 * Les métiers appellent {@link #enregistrer} dans LEUR transaction : la trace
 * et l'action qu'elle décrit committent ou s'annulent ensemble — une
 * opération annulée ne laisse jamais une trace la disant faite, et inversement.
 *
 * Rien n'est jamais mis à jour ni supprimé ici : le journal est écrit une fois
 * pour toutes (voir l'entité).
 */
@Service
@RequiredArgsConstructor
public class JournalAuditMetier {

    // ============================================
    // ACTIONS TRACÉES
    // ============================================
    public static final String CREATION = "CREATION";
    public static final String MODIFICATION = "MODIFICATION";
    public static final String SUPPRESSION = "SUPPRESSION";
    public static final String ENTREE = "ENTREE";
    public static final String TRANSFERT = "TRANSFERT";
    public static final String AFFECTATION = "AFFECTATION";
    public static final String CESSION = "CESSION";
    public static final String REBUT = "REBUT";
    public static final String DECISION_DEMANDE = "DECISION_DEMANDE";
    public static final String CLOTURE_CAMPAGNE = "CLOTURE_CAMPAGNE";
    public static final String CONNEXION = "CONNEXION";
    public static final String CONNEXION_ECHOUEE = "CONNEXION_ECHOUEE";
    public static final String EXPORT_JOURNAL = "EXPORT_JOURNAL";

    // ============================================
    // ENTITÉS TRACÉES
    // ============================================
    public static final String ENTITE_IMMOBILISATION = "Immobilisation";
    public static final String ENTITE_MOUVEMENT = "Mouvement";
    public static final String ENTITE_DEMANDE = "Demande d'immobilisation";
    public static final String ENTITE_CAMPAGNE = "Campagne d'inventaire";
    public static final String ENTITE_SESSION = "Session";
    public static final String ENTITE_JOURNAL = "Journal d'audit";
    public static final String ENTITE_UTILISATEUR = "Utilisateur";

    private static final int LONGUEUR_MAX_DESCRIPTION = 500;

    private final JournalAuditRepository journalAuditRepository;

    /**
     * Écrit une entrée de journal, dans la transaction de l'appelant.
     *
     * @param acteur     utilisateur à l'origine de l'action ({@code null} si aucun).
     * @param action     constante d'action ({@link #CREATION}, {@link #CESSION}…).
     * @param entite     libellé de l'entité touchée.
     * @param entiteId   identifiant de l'entité, ou {@code null} si sans objet.
     * @param description formulation lisible (tronquée à 500 caractères).
     */
    @Transactional
    public void enregistrer(Utilisateur acteur, String action, String entite,
                            Long entiteId, String description) {
        // Horodatage posé ici explicitement : le @PrePersist de l'entité ne
        // sert que de filet de sécurité côté JPA (le builder Lombok n'applique
        // pas les initialisateurs de champs sans @Builder.Default).
        journalAuditRepository.save(JournalAudit.builder()
                .acteur(acteur)
                .action(action)
                .entite(entite)
                .entiteId(entiteId)
                .dateAction(LocalDateTime.now())
                .description(description == null ? "" : tronquer(description))
                .build());
    }

    /**
     * Trace un export du journal — le journal rend compte de sa propre
     * extraction. La description est composée ICI à partir des critères
     * structurés : le client envoie des valeurs, jamais la phrase journalisée.
     *
     * @param acteur l'ADMIN qui tire le journal ({@code null} sans session).
     */
    @Transactional
    public void enregistrerExport(Utilisateur acteur, ExportJournalDto dto) {
        List<String> criteres = new ArrayList<>();
        if (renseigne(dto.getAction())) criteres.add("action " + dto.getAction().trim());
        if (renseigne(dto.getEntite())) criteres.add("entité " + dto.getEntite().trim());
        if (renseigne(dto.getActeurNom())) criteres.add("acteur " + dto.getActeurNom().trim());
        if (renseigne(dto.getDu()) || renseigne(dto.getAu())) {
            criteres.add("période " + (renseigne(dto.getDu()) ? dto.getDu().trim() : "…")
                    + " → " + (renseigne(dto.getAu()) ? dto.getAu().trim() : "…"));
        }
        if (renseigne(dto.getRecherche())) criteres.add("recherche « " + dto.getRecherche().trim() + " »");
        if (dto.getBien() != null) criteres.add("bien #" + dto.getBien());

        String resume = criteres.isEmpty() ? "journal complet" : String.join(", ", criteres);
        enregistrer(acteur, EXPORT_JOURNAL, ENTITE_JOURNAL, dto.getBien(),
                "Export CSV de " + Math.max(dto.getNombre(), 0) + " entrée(s) — " + resume);
    }

    private boolean renseigne(String valeur) {
        return valeur != null && !valeur.trim().isEmpty();
    }

    /**
     * Consultation filtrée — ADMIN uniquement (route fermée dans SecurityConfig).
     * Un filtre vide est neutralisé ici : le repository reçoit un vrai {@code null}.
     */
    @Transactional(readOnly = true)
    public List<JournalAuditDto> rechercher(String action, String entite, Long acteurId) {
        return journalAuditRepository.rechercher(
                        normaliser(action), normaliser(entite), acteurId)
                .stream()
                .map(JournalAuditMetier::toDto)
                .collect(Collectors.toList());
    }

    private String tronquer(String texte) {
        String propre = texte.trim();
        return propre.length() > LONGUEUR_MAX_DESCRIPTION
                ? propre.substring(0, LONGUEUR_MAX_DESCRIPTION - 1) + "…"
                : propre;
    }

    private String normaliser(String valeur) {
        return (valeur == null || valeur.trim().isEmpty()) ? null : valeur.trim();
    }

    public static JournalAuditDto toDto(JournalAudit j) {
        JournalAuditDto dto = new JournalAuditDto();
        dto.setId(j.getId());
        dto.setAction(j.getAction());
        dto.setEntite(j.getEntite());
        dto.setEntiteId(j.getEntiteId());
        dto.setDescription(j.getDescription());
        dto.setDateAction(j.getDateAction());
        if (j.getActeur() != null) {
            dto.setActeurId(j.getActeur().getId());
            dto.setActeurNom((j.getActeur().getPrenom() + " " + j.getActeur().getNom()).trim());
        }
        return dto;
    }
}
