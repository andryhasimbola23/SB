package com.auximad.gestion_immobilisation.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AmortissementDto {
    private Long id;
    private Integer annee;
    private BigDecimal valeurInitiale;
    private BigDecimal dotationAnnuelle;
    private BigDecimal amortissementCumule;
    private BigDecimal valeurNetteComptable;
}