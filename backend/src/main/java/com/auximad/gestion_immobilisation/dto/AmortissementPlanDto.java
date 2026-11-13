package com.auximad.gestion_immobilisation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AmortissementPlanDto {
    private Long immobilisationId;
    private String codeUnique;
    private String designation;
    private BigDecimal valeurAchat;
    private BigDecimal tauxAmortissement;
    private Integer dureeAmortissement; // en années
    private BigDecimal vncActuelle; // VNC à aujourd'hui
    private List<AmortissementDto> lignes;
}