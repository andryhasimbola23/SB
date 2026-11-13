package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DemandeDto {

    private Long id;

    /**
     * Référence de suivi affichée à l'employé (DEM-2026-0007).
     * Calculée à la lecture, jamais stockée : dérivée de l'id et de l'année de
     * création, tous deux immuables — donc unique sans compteur ni verrou.
     */
    private String reference;

    @NotBlank(message = "La désignation est obligatoire")
    @Size(max = 150, message = "La désignation ne doit pas dépasser 150 caractères")
    private String designation;

    @NotBlank(message = "Le motif de la demande est obligatoire")
    @Size(max = 2000, message = "Le motif ne doit pas dépasser 2000 caractères")
    private String motif;

    @Min(value = 1, message = "La quantité doit être au moins égale à 1")
    private Integer quantite = 1;

    /** NORMALE | URGENTE */
    private String priorite;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate besoinLe;

    /** EN_ATTENTE | APPROUVEE | REJETTEE | ANNULEE — imposé en service, pas au client. */
    private String statut;

    private Long categorieId;
    private String categorieNom;

    private Long serviceId;
    private String serviceNom;
    /** Libellé affichable : nom du service ou « Non affecté » si service null. */
    private String serviceAffiche;

    private Long demandeurId;
    private String demandeurNom;

    private Long traiteeParId;
    private String traiteeParNom;

    /** Motif du refus / décision. */
    private String commentaireTraitement;
    private LocalDateTime dateTraitement;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateCreation;

    /** Bien livré rattaché à la demande (facultatif). */
    private Long immobilisationId;
    private String immobilisationCode;
    private String immobilisationDesignation;

    /**
     * Mouvement créé par la livraison — lien inverse de {@code Mouvement.demande}.
     * Seules les demandes ayant livré un bien en portent un ; la liste complète
     * les obtient par une seule requête par lot, pas une par ligne.
     */
    private Long mouvementId;
    private String mouvementType;
}
