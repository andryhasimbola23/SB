package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CampagneInventaireDto {

    private Long id;

    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;

    @NotNull(message = "La date de début est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private String statut;

    private Long agenceId;
    private Long utilisateurId;

    // Champs en lecture seule
    private String agenceNom;
    private String agenceCode;
    private String utilisateurNom;
    private LocalDateTime dateCreation;

    // Statistiques
    private Long totalImmobilisations;
    private Long totalScannees;
    private Long totalPresentes;
    private Long totalManquantes;
    private Long totalMauvaisEtat;
}