package com.auximad.gestion_immobilisation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Mise à jour du profil par son PROPRE titulaire (PATCH /api/auth/profil).
 *
 * Volontairement étroit : email, téléphone et photo. Nom, rôle, service et
 * agence sont des prérogatives de l'ADMIN — ce DTO ne permet même pas de les
 * tenter. Tous les champs sont optionnels : une seule modification à la fois
 * est recevable (photo seule, téléphone seul…), un champ non transmis reste
 * inchangé.
 */
@Data
public class ProfilDto {

    @Email(message = "L'email doit être valide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    private String email;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères")
    private String telephone;

    /** URL de la photo déjà déposée sur /api/upload/photo. */
    @Size(max = 255, message = "L'URL de la photo ne doit pas dépasser 255 caractères")
    private String photo;
}
