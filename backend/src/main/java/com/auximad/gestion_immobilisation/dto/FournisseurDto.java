package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FournisseurDto {

    private Long id;

    @NotBlank(message = "Le nom du fournisseur est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nomFournisseur;

    private String adresseFournisseur;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères")
    private String telephoneFournisseur;

    @Email(message = "L'email doit être valide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    private String emailFournisseur;

    @Size(max = 255, message = "L'URL ne doit pas dépasser 255 caractères")
    private String siteWeb;

    private Boolean actif;

    private LocalDateTime dateCreation;
}