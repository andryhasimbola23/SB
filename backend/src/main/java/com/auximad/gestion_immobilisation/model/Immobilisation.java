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
@Table(name = "immobilisation")
public class Immobilisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "immobilisation_id")
    private Long id;

    @Column(name = "code_unique", unique = true, length = 50)
    private String codeUnique;

    @Column(nullable = false, length = 150)
    private String designation;

    @Column(name = "date_acquisition", nullable = false)
    private LocalDateTime dateAcquisition;

    @Column(name = "date_mise_service", nullable = false)
    private LocalDateTime dateMiseService;

    @Column(name = "valeur_achat", nullable = false, precision = 15, scale = 2)
    private BigDecimal valeurAchat;

    @Column(nullable = false, length = 30)
    private String statut; // ACTIF, CEDE, REBUTE

    @Column(name = "etat_physique", nullable = false, length = 50)
    private String etatPhysique; // NEUF, BON, MAUVAIS, HORS_SERVICE

    @Column(length = 255)
    private String photo;

    @Column(nullable = false, length = 50)
    private String marque;

    @Column(name = "taux_amortissement", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxAmortissement;

    @Column(name = "type_materiel", nullable = false, length = 50)
    private String typeMateriel; // UCB, ARM, CFR...

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String facture;

    @Column(name = "qr_code", length = 255)
    private String qrCode;

    /**
     * Verrouillage optimiste : toute écriture concurrente sur la même fiche
     * échoue avec un 409 lisible au lieu d'écraser silencieusement la
     * modification d'un autre utilisateur.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fournisseur_id")
    private Fournisseur fournisseur;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
        if (this.statut == null) this.statut = "ACTIF";
        if (this.etatPhysique == null) this.etatPhysique = "BON";
    }

    /**
     * Note sur les données antérieures à {@code @Version} : la colonne `version`
     * a été créée après coup par {@code ddl-auto=update} et les lignes d'origine
     * la portent à {@code NULL} — ce qui fait échouer le flush par déboxing
     * d'une {@code Long} nulle. Un {@code @PostLoad} ne suffit pas : Hibernate
     * compare à l'instantané capté au chargement, pas au champ. C'est
     * {@code VersionRepairRunner} qui garantit l'absence de {@code NULL} au
     * démarrage.
     */
}