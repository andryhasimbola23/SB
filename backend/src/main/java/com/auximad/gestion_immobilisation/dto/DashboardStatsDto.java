package com.auximad.gestion_immobilisation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private Long totalImmobilisations;
    private Long totalActifs;
    private Long totalCedes;
    private Long totalRebutes;
    private Long totalMauvaisEtat;
    private Long totalHorsService;
    private BigDecimal valeurTotaleParc;
    private BigDecimal vncTotale;
    private Long mouvementsCeMois;
    private Long nouveauxBiensCeMois;
    private Long campagnesEnCours;
    /** Part déjà amortie du parc actif, en % (1 − VNC / valeur d'acquisition). */
    private java.math.BigDecimal tauxAmortissementGlobal;
    /** Actifs mis en service depuis plus de 5 ans — parc vieillissant. */
    private Long biensAnciens;
    private Long totalUtilisateurs;
    private Long totalAgences;
    private Long totalServices;
    private Long totalCategories;
    private Long totalFournisseurs;
}