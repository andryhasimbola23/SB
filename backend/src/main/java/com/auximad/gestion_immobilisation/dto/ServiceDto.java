package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceDto {

    private Long id;

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 20, message = "Le code ne doit pas dépasser 20 caractères")
    private String code;

    @NotBlank(message = "Le nom du service est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nomService;

    private String description;

    private Boolean actif;

    @NotNull(message = "L'agence est obligatoire")
    private Long agenceId;

    // Champs en lecture seule (retournés par l'API)
    private String agenceNom;
    private String agenceCode;
    private LocalDateTime dateModification;
}