package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.NotificationDto;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Notification;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.NotificationRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notifications in-app (cloche) — sans e-mail.
 *
 * Les métiers appellent {@link #notifier} / {@link #notifierAdmins} au moment
 * où l'événement se produit, dans leur propre transaction : une notification
 * n'est jamais le signaleur du succès d'une opération (si la transaction est
 * annulée, la notification l'est avec elle).
 *
 * Deux garde-fous systématiques :
 *  - pas d'auto-notification : l'acteur ne reçoit jamais ce qu'il vient de faire ;
 *  - les destinataires inactifs sont ignorés silencieusement — une notification
 *    pour un compte désactivé n'a aucun destinataire réel.
 */
@Service
@RequiredArgsConstructor
public class NotificationMetier {

    // Types d'événements (chaînes figées — voir la note sur l'entité).
    public static final String NOUVELLE_DEMANDE = "NOUVELLE_DEMANDE";
    public static final String DEMANDE_APPROUVEE = "DEMANDE_APPROUVEE";
    public static final String DEMANDE_REJETEE = "DEMANDE_REJETEE";
    public static final String BIEN_CEDER = "BIEN_CEDER";
    public static final String BIEN_REBUTE = "BIEN_REBUTE";
    public static final String CAMPAGNE_CLOTUREE = "CAMPAGNE_CLOTUREE";

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    // Temps réel best effort : null dans les tests unitaires sans contexte Spring,
    // et jamais bloquant (voir RealtimeMetier).
    private final RealtimeMetier realtimeMetier;

    /**
     * Écrit une notification pour un destinataire.
     *
     * Ne lève jamais : appeler un destinataire disparu ou l'acteur lui-même
     * n'est pas une erreur métier, c'est un événement sans notification.
     *
     * @param acteurId  utilisateur à l'origine de l'événement (non notifié), ou null.
     */
    @Transactional
    public void notifier(Long destinataireId, String type, String titre, String message,
                         String lien, Long acteurId) {
        if (destinataireId == null || destinataireId.equals(acteurId)) {
            return;
        }
        Utilisateur destinataire = utilisateurRepository.findById(destinataireId).orElse(null);
        if (destinataire == null || !Boolean.TRUE.equals(destinataire.getActif())) {
            return;
        }
        Notification notification = Notification.builder()
                .destinataire(destinataire)
                .type(type)
                .titre(titre)
                .message(message)
                .lien(lien)
                .dateCreation(LocalDateTime.now()) // @PrePersist : filet de sécurité JPA
                .build();
        notificationRepository.save(notification);

        // Temps réel : la cloche de ce destinataire (tous ses navigateurs) est
        // prévenue dans la foulée, après commit — c'est le seul point de
        // publication, donc toute notification du système est poussée, y compris
        // celles créées par MouvementMetier et InventaireMetier.
        if (realtimeMetier != null) {
            realtimeMetier.publierNotification(destinataire.getId(), toDto(notification));
        }
    }

    /**
     * Notifie tous les administrateurs actifs — le point d'entrée des événements
     * de gestion du parc (nouvelle demande, cession, rebut).
     */
    @Transactional
    public void notifierAdmins(String type, String titre, String message,
                               String lien, Long acteurId) {
        for (Utilisateur admin : utilisateurRepository.findByRole(Role.ADMIN)) {
            notifier(admin.getId(), type, titre, message, lien, acteurId);
        }
    }

    /** Notifications du connecté, plus récentes en tête. */
    @Transactional(readOnly = true)
    public List<NotificationDto> mesNotifications(Long destinataireId) {
        return notificationRepository.findByDestinataireIdOrderByDateCreationDesc(destinataireId)
                .stream()
                .map(NotificationMetier::toDto)
                .collect(Collectors.toList());
    }

    /** Valeur du badge de la cloche. */
    @Transactional(readOnly = true)
    public long compterNonLues(Long destinataireId) {
        return notificationRepository.countByDestinataireIdAndLueFalse(destinataireId);
    }

    /**
     * Marque une notification comme lue.
     *
     * Seul le destinataire peut le faire : la notification d'autrui donne un
     * 404 identique à une notification inexistante (pas d'énumération).
     */
    @Transactional
    public NotificationDto marquerLue(Long id, Long destinataireId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification non trouvée"));
        if (!notification.getDestinataire().getId().equals(destinataireId)) {
            throw new ResourceNotFoundException("Notification non trouvée");
        }
        if (!notification.isLue()) {
            notification.setLue(true);
            notification.setDateLue(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    /** « Tout marquer comme lu » — une seule requête UPDATE. */
    @Transactional
    public int toutMarquerLue(Long destinataireId) {
        return notificationRepository.marquerToutesLues(destinataireId, LocalDateTime.now());
    }

    public static NotificationDto toDto(Notification n) {
        NotificationDto dto = new NotificationDto();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setTitre(n.getTitre());
        dto.setMessage(n.getMessage());
        dto.setLien(n.getLien());
        dto.setLue(n.isLue());
        dto.setDateCreation(n.getDateCreation());
        dto.setDateLue(n.getDateLue());
        return dto;
    }
}
