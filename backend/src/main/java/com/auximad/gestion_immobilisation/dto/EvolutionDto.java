package com.auximad.gestion_immobilisation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionDto {
    private Integer annee;
    private Long nombre;
    private BigDecimal valeur;
}