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
@Table(name = "mouvement")
public class Mouvement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mouvement_id")
    private Long id;

    @Column(name = "type_mouvement", nullable = false, length = 50)
    private String typeMouvement; // ENTREE, TRANSFERT, CESSION, REBUT

    @Column(name = "date_mouvement", nullable = false)
    private LocalDateTime dateMouvement = LocalDateTime.now();

    @Column(name = "ancienne_localisation", length = 255)
    private String ancienneLocalisation;

    @Column(name = "nouvelle_localisation", length = 255)
    private String nouvelleLocalisation;

    @Column(name = "valeur_vnc", precision = 15, scale = 2)
    private BigDecimal valeurVnc;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "prix_vente", precision = 15, scale = 2)
    private BigDecimal prixVente; // Pour les cessions

    @Column(name = "numero_facture", length = 100)
    private String numeroFacture;

    @Column(name = "plus_moins_value", precision = 15, scale = 2)
    private BigDecimal plusMoinsValue; // Calculé pour les cessions

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "immobilisation_id", nullable = false)
    private Immobilisation immobilisation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    /**
     * Demande d'immobilisation qui a déclenché ce mouvement (facultatif).
     *
     * Lien inverse de {@code DemandeImmobilisation.immobilisation} : la demande
     * sait ce qu'elle a livré, le mouvement sait d'où il vient. Nullable car la
     * plupart des mouvements (entrée, cession, rebut, transfert manuel) n'ont
     * aucune demande derrière.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "demande_id")
    private DemandeImmobilisation demande;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
        if (this.dateMouvement == null) this.dateMouvement = LocalDateTime.now();
    }
}