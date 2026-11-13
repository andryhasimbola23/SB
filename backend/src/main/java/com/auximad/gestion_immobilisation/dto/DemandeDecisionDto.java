package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Décision d'un administrateur sur une demande.
 *
 * {@code decision} vaut APPROUVEE ou REJETTEE — énuméré en service plutôt qu'en
 * annotation pour ne pas permettre ANNULEE, qui n'est pas une décision.
 */
@Data
public class DemandeDecisionDto {

    @NotBlank(message = "La décision est obligatoire")
    private String decision;

    /**
     * Motif du refus. Obligatoire si la décision est un refus — un « non » muet
     * renvoie l'employé en arrière sans qu'il puisse corriger quoi que ce soit.
     */
    @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
    private String commentaire;

    /** Bien finalement remis (facultatif, approbation seulement). */
    private Long immobilisationId;

    /**
     * Service de destination du bien remis.
     *
     * Préféré à celui de la demande quand il est renseigné — c'est le cas d'une
     * demande d'employé non affecté (service null), pour lequel la destination * doit être choisie explicitement.
     */
    private Long destinationServiceId;
}
