package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ligne_inventaire")
public class LigneInventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ligne_id")
    private Long id;

    @Column(name = "date_scan", nullable = false)
    private LocalDateTime dateScan = LocalDateTime.now();

    @Column(name = "localisation_constatee", length = 255)
    private String localisationConstatee;

    @Column(name = "etat_constate", length = 50)
    private String etatConstate; // PRESENT, MANQUANT, MAUVAIS_ETAT

    @Column(columnDefinition = "TEXT")
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campagne_id", nullable = false)
    private CampagneInventaire campagne;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "immobilisation_id", nullable = false)
    private Immobilisation immobilisation;

    @PrePersist
    protected void onCreate() {
        if (this.dateScan == null) this.dateScan = LocalDateTime.now();
    }
}