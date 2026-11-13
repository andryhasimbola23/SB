package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MouvementDto {

    private Long id;

    private String typeMouvement; // ENTREE, TRANSFERT, CESSION, REBUT

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateMouvement;

    private String ancienneLocalisation;
    private String nouvelleLocalisation;
    private BigDecimal valeurVnc;
    private String commentaire;
    private BigDecimal prixVente;
    private String numeroFacture;
    private BigDecimal plusMoinsValue;

    @NotNull(message = "L'immobilisation est obligatoire")
    private Long immobilisationId;

    private Long utilisateurId;

    /** Demande d'immobilisation qui a déclenché le mouvement (facultatif). */
    private Long demandeId;

    // Champs en lecture seule
    private String immobilisationCode;
    private String immobilisationDesignation;
    private String utilisateurNom;
    private String demandeReference;
    private LocalDateTime dateCreation;

    // Pour les transferts : nouveaux service / agence
    private Long nouveauServiceId;
    private Long nouvelleAgenceId;
}