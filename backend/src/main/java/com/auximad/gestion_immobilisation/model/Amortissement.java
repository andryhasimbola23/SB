package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "amortissement",
       uniqueConstraints = @UniqueConstraint(columnNames = {"immobilisation_id", "annee"}))
public class Amortissement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "amortissement_id")
    private Long id;

    @Column(nullable = false)
    private Integer annee;

    @Column(name = "valeur_initiale", nullable = false, precision = 15, scale = 2)
    private BigDecimal valeurInitiale;

    @Column(name = "dotation_annuelle", nullable = false, precision = 15, scale = 2)
    private BigDecimal dotationAnnuelle;

    @Column(name = "amortissement_cumule", nullable = false, precision = 15, scale = 2)
    private BigDecimal amortissementCumule;

    @Column(name = "valeur_nette_comptable", nullable = false, precision = 15, scale = 2)
    private BigDecimal valeurNetteComptable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "immobilisation_id", nullable = false)
    private Immobilisation immobilisation;
}