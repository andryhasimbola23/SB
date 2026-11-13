package com.auximad.gestion_immobilisation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ImmobilisationDto {

    private Long id;

    private String codeUnique; // Généré automatiquement

    @NotBlank(message = "La désignation est obligatoire")
    @Size(max = 150, message = "La désignation ne doit pas dépasser 150 caractères")
    private String designation;

    @NotNull(message = "La date d'acquisition est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateAcquisition;

    @NotNull(message = "La date de mise en service est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateMiseService;

    @NotNull(message = "La valeur d'achat est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "La valeur doit être supérieure à 0")
    private BigDecimal valeurAchat;

    private String statut;

    @NotBlank(message = "L'état physique est obligatoire")
    private String etatPhysique;

    private String photo;

    @NotBlank(message = "La marque est obligatoire")
    @Size(max = 50, message = "La marque ne doit pas dépasser 50 caractères")
    private String marque;

    @NotNull(message = "Le taux d'amortissement est obligatoire")
    @DecimalMin(value = "0.0", message = "Le taux doit être positif")
    @DecimalMax(value = "100.0", message = "Le taux ne peut pas dépasser 100%")
    private BigDecimal tauxAmortissement;

    @NotBlank(message = "Le type de matériel est obligatoire")
    private String typeMateriel;

    private String description;

    private String facture;

    private String qrCode;

    @NotNull(message = "Le service est obligatoire")
    private Long serviceId;

    @NotNull(message = "L'agence est obligatoire")
    private Long agenceId;

    @NotNull(message = "La catégorie est obligatoire")
    private Long categorieId;

    private Long fournisseurId;

    // Champs en lecture seule
    private String serviceNom;
    private String agenceNom;
    private String agenceCode;
    private String categorieNom;
    private String categorieCode;
    private String fournisseurNom;
    private LocalDateTime dateCreation;
}