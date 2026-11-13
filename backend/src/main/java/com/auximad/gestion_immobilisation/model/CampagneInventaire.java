package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campagne_inventaire")
public class CampagneInventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campagne_id")
    private Long id;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(nullable = false, length = 30)
    private String statut; // EN_COURS, CLOTURE

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    /** Verrouillage optimiste (voir {@link Immobilisation}). */
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @OneToMany(mappedBy = "campagne", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneInventaire> lignes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
        if (this.statut == null) this.statut = "EN_COURS";
    }
}