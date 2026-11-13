package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Demande d'immobilisation déposée par un employé, à traiter par un administrateur.
 *
 * Rattachement : {@link #service} est figé à la création à partir du service du
 * demandeur — la demande ne doit pas suivre l'employé s'il change d'affectation.
 * Il reste nullable pour un employé non affecté : un besoin réel ne disparaît pas
 * parce que la fiche est incomplète.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "demande_immobilisation")
public class DemandeImmobilisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "demande_id")
    private Long id;

    /** Ce qui est demandé, saisie libre par l'employé. */
    @Column(nullable = false, length = 150)
    private String designation;

    /** Justification : pourquoi ce bien est nécessaire. */
    @Column(columnDefinition = "TEXT")
    private String motif;

    @Column(nullable = false)
    private Integer quantite = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "priorite", nullable = false, length = 20)
    private PrioriteDemande priorite;

    /** Date à laquelle le matériel est effectivement nécessaire (facultatif). */
    @Column(name = "besoin_le")
    private LocalDate besoinLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutDemande statut;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "demandeur_id", nullable = false)
    private Utilisateur demandeur;

    /** Service du demandeur, figé à la création. Nullable (employé non affecté). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    private Service service;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "traitee_par_id")
    private Utilisateur traiteePar;

    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    /** Motif du refus, obligatoire si REJETTEE. */
    @Column(name = "commentaire_traitement", columnDefinition = "TEXT")
    private String commentaireTraitement;

    /** Bien finalement rattaché à la demande, à renseigner par l'admin (facultatif). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "immobilisation_id")
    private Immobilisation immobilisation;

    /** Verrouillage optimiste (voir {@link Immobilisation}). */
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
        if (this.statut == null) this.statut = StatutDemande.EN_ATTENTE;
        if (this.priorite == null) this.priorite = PrioriteDemande.NORMALE;
        if (this.quantite == null || this.quantite < 1) this.quantite = 1;
    }
}
