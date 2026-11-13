package com.auximad.gestion_immobilisation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepartitionDto {
    private String label;

    /**
     * Sigle du service (IT, SAG, RH…), renseigné pour la répartition par
     * service seulement — c'est lui qui tient sur l'axe d'un graphique, le nom
     * complet restant réservé à l'infobulle.
     */
    private String code;

    private Long nombre;
    private BigDecimal valeur;

    public RepartitionDto(String label, Long nombre, BigDecimal valeur) {
        this.label = label;
        this.nombre = nombre;
        this.valeur = valeur;
    }

    public RepartitionDto(String label, Long nombre) {
        this.label = label;
        this.nombre = nombre;
        this.valeur = BigDecimal.ZERO;
    }
}