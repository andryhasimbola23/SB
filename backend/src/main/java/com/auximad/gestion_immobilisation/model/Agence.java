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
@Table(name = "agence")
public class Agence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agence_id")
    private Long id;

    @Column(name = "code_agence", nullable = false, unique = true, length = 10)
    private String codeAgence;

    @Column(name = "nom_agence", nullable = false, length = 255)
    private String nomAgence;

    @Column(name = "ville_agence", length = 100)
    private String villeAgence;

    @Column(name = "email_agence", length = 100)
    private String emailAgence;

    @Column(name = "telephone_agence", length = 20)
    private String telephoneAgence;

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
        if (this.actif == null) {
            this.actif = true;
        }
    }
}