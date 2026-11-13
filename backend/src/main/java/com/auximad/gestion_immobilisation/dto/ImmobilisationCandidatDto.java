package com.auximad.gestion_immobilisation.dto;

import lombok.Data;

/**
 * Bien proposé à l'administrateur lorsqu'il traite une demande d'immobilisation.
 *
 * Ne transporte que ce dont l'administrateur a besoin pour choisir : le bien,
 * où il est, et en quel état. Le service courant permet d'annoncer la suite
 * avant de la valider — TRANSFERT s'il change, AFFECTATION s'il ne bouge pas.
 *
 * La sélection n'est qu'une intention : `DemandeMetier` revalide le bien au
 * moment de la décision. Ce DTO peut donc être obsolète au moment où il est lu —
 * d'où le rechargement de la liste après chaque traitement.
 */
@Data
public class ImmobilisationCandidatDto {

    private Long id;
    private String codeUnique;
    private String designation;
    private String marque;
    /** NEUF, BON, MAUVAIS, HORS_SERVICE */
    private String etatPhysique;

    private Long serviceId;
    private String serviceNom;

    private Long categorieId;
    private String categorieNom;

    private String agenceNom;
}
