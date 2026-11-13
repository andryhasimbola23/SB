package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UtilisateurDto {

    private Long id;

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(max = 25, message = "Le nom d'utilisateur ne doit pas dépasser 25 caractères")
    private String nomUtilisateur;

    /**
     * Mot de passe en clair (uniquement en entrée pour la création/modification).
     * Sera haché avant sauvegarde. Non retourné dans les réponses.
     */
    @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
    private String motDePasse;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    private String email;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 50, message = "Le prénom ne doit pas dépasser 50 caractères")
    private String prenom;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères")
    private String telephone;

    private String photo;

    @NotBlank(message = "Le rôle est obligatoire")
    private String role; // ADMIN ou EMPLOYE

    private Boolean actif;

    private Long serviceId;
    private Long agenceId;

    // Champs en lecture seule
    private String serviceNom;
    private String agenceNom;
    private LocalDateTime dateCreation;
}