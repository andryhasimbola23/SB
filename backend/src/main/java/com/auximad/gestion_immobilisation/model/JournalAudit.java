package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Journal d'audit — trace des actions sensibles du parc et des sessions.
 *
 * Table **append-only** : aucune mise à jour ni suppression n'est offerte par
 * l'API, et la date d'action n'est jamais modifiable ({@code updatable = false}).
 * Une trace d'audit qu'on peut réécrire ne trace rien.
 *
 * L'acteur peut être {@code null} : certaines actions n'ont pas d'auteur
 * authentifié (tentative de connexion échouée par un intrus). Connaître la
 * tentative vaut mieux qu'une trace absente faute d'utilisateur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "journal_audit", indexes = {
        @Index(name = "idx_journal_audit_date", columnList = "date_action"),
        @Index(name = "idx_journal_audit_acteur", columnList = "acteur_id"),
})
public class JournalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_id")
    private Long id;

    /** Qui — {@code null} si action sans session authentifiée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acteur_id")
    private Utilisateur acteur;

    /** Que — constantes d'action de {@code JournalAuditMetier}. */
    @Column(nullable = false, length = 40)
    private String action;

    /** Sur quoi — libellé de l'entité métier (« Immobilisation », « Mouvement »…). */
    @Column(nullable = false, length = 50)
    private String entite;

    /** Identifiant de l'entité touchée, quand il existe. */
    @Column(name = "entite_id")
    private Long entiteId;

    /** Formulation lisible, destinée à un humain — pas un dump technique. */
    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "date_action", nullable = false, updatable = false)
    private LocalDateTime dateAction = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (this.dateAction == null) this.dateAction = LocalDateTime.now();
    }
}
