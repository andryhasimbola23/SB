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
@Table(name = "fournisseur")
public class Fournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fournisseur_id")
    private Long id;

    @Column(name = "nom_fournisseur", nullable = false, length = 100)
    private String nomFournisseur;

    @Column(name = "adresse_fournisseur", columnDefinition = "TEXT")
    private String adresseFournisseur;

    @Column(name = "telephone_fournisseur", length = 20)
    private String telephoneFournisseur;

    @Column(name = "email_fournisseur", length = 100)
    private String emailFournisseur;

    @Column(name = "site_web", length = 255)
    private String siteWeb;

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