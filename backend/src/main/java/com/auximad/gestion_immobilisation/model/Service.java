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
@Table(name = "service")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "nom_service", nullable = false, length = 100)
    private String nomService;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(nullable = false)
    private Boolean actif = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    @PrePersist
    protected void onCreate() {
        if (this.actif == null) this.actif = true;
        this.dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.dateModification = LocalDateTime.now();
    }
}