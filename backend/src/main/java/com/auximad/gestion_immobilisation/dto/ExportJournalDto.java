package com.auximad.gestion_immobilisation.dto;

import lombok.Data;

/**
 * Requête de traçage d'un export du journal.
 *
 * L'extraction du journal est une action sensible comme une autre : qui tire
 * le journal doit y laisser sa trace. La requête décrit le JEU EXPORTÉ
 * (nombre d'entrées + filtres réellement appliqués par l'écran) ; le serveur
 * en compose une formulation lisible — le client n'envoie jamais de texte
 * libre destiné à être journalisé tel quel.
 */
@Data
public class ExportJournalDto {

    /** Nombre d'entrées réellement exportées. */
    private int nombre;

    // ==== Filtres appliqués lors de l'export — tous optionnels ====

    private String action;

    private String entite;

    /** Nom complet de l'acteur filtré, le cas échéant. */
    private String acteurNom;

    /** Bornes de période au format ISO (yyyy-MM-dd), affichées telles quelles. */
    private String du;
    private String au;

    private String recherche;

    /** Identifiant de l'entité dont l'historique a été exporté, le cas échéant. */
    private Long bien;
}
