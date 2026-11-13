package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LigneInventaireDto {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateScan;

    private String localisationConstatee;
    private String etatConstate; // PRESENT, MANQUANT, MAUVAIS_ETAT
    private String observation;

    @NotNull(message = "La campagne est obligatoire")
    private Long campagneId;

    @NotNull(message = "L'immobilisation est obligatoire")
    private Long immobilisationId;

    // Champs en lecture seule
    private String immobilisationCode;
    private String immobilisationDesignation;
    private String immobilisationPhoto;
    private String immobilisationService;
    private String immobilisationAgence;
    private String immobilisationEtatTheorique;
}