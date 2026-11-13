package com.auximad.gestion_immobilisation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notification in-app (aucun e-mail) : une ligne par destinataire, écrite par
 * le métier au moment où l'événement se produit.
 *
 * La copie par destinataire (et non un « post » lu par tous) est assumée :
 * le marquage lu est une information personnelle, et le compteur d'un
 * destinataire se lit par un simple index, sans jointure ni agrégation.
 *
 * Les types sont des chaînes figées par convention (constantes dans
 * {@code NotificationMetier}) : l'énumération formaliserait peu de valeurs
 * et imposerait une migration à chaque nouvel événement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_destinataire", columnList = "destinataire_id"),
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    /** Seul le destinataire lit (et marque lu) ses notifications. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    /** Type d'événement (constantes dans {@code NotificationMetier}). */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(nullable = false, length = 500)
    private String message;

    /** Route frontend à ouvrir au clic (null = simple information). */
    @Column(length = 200)
    private String lien;

    @Column(nullable = false)
    private boolean lue = false;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "date_lue")
    private LocalDateTime dateLue;

    @PrePersist
    void onCreate() {
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
    }
}
