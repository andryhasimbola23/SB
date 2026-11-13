package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CategorieDto {

    private Long id;

    @NotBlank(message = "Le code comptable est obligatoire")
    @Size(max = 20, message = "Le code ne doit pas dépasser 20 caractères")
    private String codeComptable;

    @NotBlank(message = "Le nom de la catégorie est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nomCategorie;

    @Size(max = 150, message = "Le libellé ne doit pas dépasser 150 caractères")
    private String libelle;

    @NotBlank(message = "Le type est obligatoire")
    private String typeImmobilisation; // COR, INC, FIN

    private Integer classeCompte;

    @DecimalMin(value = "0.0", message = "Le taux doit être positif")
    @DecimalMax(value = "100.0", message = "Le taux ne peut pas dépasser 100%")
    private BigDecimal tauxAmortissementParDefaut;

    @Min(value = 0, message = "La durée doit être positive")
    private Integer dureeUtilisation;

    private Boolean actif;

    private LocalDateTime dateCreation;
}