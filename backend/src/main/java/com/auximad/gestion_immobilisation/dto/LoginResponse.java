package com.auximad.gestion_immobilisation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private Long id;
    private String token;
    private String nomUtilisateur;
    private String role;
    private String nomComplet;
    /** Service rattaché — délimite le périmètre de consultation (null pour un ADMIN). */
    private Long serviceId;
    private String serviceNom;
}
