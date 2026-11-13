package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "categorie")
public class Categorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categorie_id")
    private Long id;

    @Column(name = "code_comptable", nullable = false, unique = true, length = 20)
    private String codeComptable;

    @Column(name = "nom_categorie", nullable = false, length = 100)
    private String nomCategorie;

    @Column(length = 150)
    private String libelle;

    @Column(name = "type_immobilisation", length = 10)
    private String typeImmobilisation; // COR = Corporelle, INC = Incorporelle, FIN = Financière

    @Column(name = "classe_compte")
    private Integer classeCompte; // 20, 21, 27, etc.

    @Column(name = "taux_amortissement_par_defaut", precision = 5, scale = 2)
    private BigDecimal tauxAmortissementParDefaut;

    @Column(name = "duree_utilisation")
    private Integer dureeUtilisation; // en années

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.actif == null) this.actif = true;
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
    }
}