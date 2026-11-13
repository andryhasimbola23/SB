package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgenceDto {

    private Long id;

    @NotBlank(message = "Le code agence est obligatoire")
    @Size(max = 10, message = "Le code agence ne doit pas dépasser 10 caractères")
    private String codeAgence;

    @NotBlank(message = "Le nom de l'agence est obligatoire")
    @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
    private String nomAgence;

    @Size(max = 100, message = "La ville ne doit pas dépasser 100 caractères")
    private String villeAgence;

    @Email(message = "L'email doit être valide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    private String emailAgence;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères")
    private String telephoneAgence;

    private Boolean actif;

    private LocalDateTime dateCreation;
}