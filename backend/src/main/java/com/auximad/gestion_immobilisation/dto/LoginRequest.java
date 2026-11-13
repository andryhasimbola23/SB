package com.auximad.gestion_immobilisation.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String nomUtilisateur;
    private String motDePasse;
}