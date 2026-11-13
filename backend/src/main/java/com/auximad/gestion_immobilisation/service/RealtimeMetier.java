package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.DemandeTempsReelDto;
import com.auximad.gestion_immobilisation.dto.NotificationDto;
import com.auximad.gestion_immobilisation.dto.NotificationTempsReelDto;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.NotificationRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publication des événements temps réel vers les clients connectés (STOMP).
 *
 * Deux files privées, une par besoin de synchronisation :
 *  - {@code /user/queue/notifications} : la cloche (notification + compteur) ;
 *  - {@code /user/queue/demandes}      : « rechargez la liste des demandes ».
 *
 * Le routage passe par {@link SimpMessagingTemplate#convertAndSendToUser} avec
 * l'id utilisateur comme nom de principal — le même que posé par
 * {@code JwtHandshakeHandler} au handshake. Un client ne peut donc recevoir que
 * les événements de sa propre file : aucun abonnement n'est possible sur celle
 * d'autrui.
 *
 * Trois garde-fous, parce que le temps réel est un confort, jamais une
 * condition de succès d'une opération métier :
 *  - la publication est déportée <strong>après commit</strong> — inutile
 *    d'annoncer une donnée que la transaction n'a pas encore écrite (et impossible
 *    d'annoncer une opération annulée) ;
 *  - toute erreur de broker est absorbée et journalisée : un websocket à terre
 *    ne transforme jamais un dépôt de demande en 500 ;
 *  - les appels depuis les métiers sont neutralisés si le bean est absent
 *    (tests unitaires sans contexte Spring).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeMetier {

    /** File des notifications (cloche) — préfixée /user côté client. */
    public static final String FILE_NOTIFICATIONS = "/queue/notifications";

    /** File de rafraîchissement des listes de demandes. */
    public static final String FILE_DEMANDES = "/queue/demandes";

    // Événements de synchronisation des demandes (valeurs figées, exposées au client).
    public static final String EVT_NOUVELLE_DEMANDE = "NOUVELLE_DEMANDE";
    public static final String EVT_DEMANDE_DECIDEE = "DEMANDE_DECIDEE";
    public static final String EVT_DEMANDE_ANNULEE = "DEMANDE_ANNULEE";

    private final SimpMessagingTemplate messagingTemplate;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Pousse une notification fraîchement enregistrée à son destinataire,
     * avec le compteur exact de non-lues recalculé après commit.
     */
    public void publierNotification(Long destinataireId, NotificationDto notification) {
        if (destinataireId == null || notification == null) {
            return;
        }
        apresCommit(() -> {
            long nonLues = notificationRepository.countByDestinataireIdAndLueFalse(destinataireId);
            envoyer(destinataireId, FILE_NOTIFICATIONS,
                    new NotificationTempsReelDto(notification, nonLues));
        });
    }

    /** Signale à un utilisateur qu'une demande a changé (son affaire). */
    public void publierDemandes(Long destinataireId, String evenement, Long demandeId) {
        if (destinataireId == null || evenement == null || demandeId == null) {
            return;
        }
        apresCommit(() -> envoyer(destinataireId, FILE_DEMANDES,
                new DemandeTempsReelDto(evenement, demandeId)));
    }

    /**
     * Signale à tous les administrateurs qu'une demande a changé : création,
     * décision, annulation — chacun recharge sa liste à l'identique.
     */
    public void publierDemandesAuxAdmins(String evenement, Long demandeId) {
        if (evenement == null || demandeId == null) {
            return;
        }
        apresCommit(() -> {
            for (Utilisateur admin : utilisateurRepository.findByRole(Role.ADMIN)) {
                if (Boolean.TRUE.equals(admin.getActif())) {
                    envoyer(admin.getId(), FILE_DEMANDES,
                            new DemandeTempsReelDto(evenement, demandeId));
                }
            }
        });
    }

    /**
     * Exécute après le commit de la transaction courante (ou immédiatement s'il
     * n'y en a pas) : le client ne reçoit que des événements confirmés en base.
     * Toute erreur est absorbée — une publication qui échoue ne doit jamais
     * remonter jusqu'à l'appelant, l'opération métier étant déjà committée.
     */
    private void apresCommit(Runnable action) {
        Runnable securise = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("Publication temps réel impossible : {}", e.getMessage());
            }
        };
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        securise.run();
                    }
                });
            } else {
                securise.run();
            }
        } catch (Exception e) {
            log.warn("Différé de publication temps réel impossible : {}", e.getMessage());
        }
    }

    /** Envoi best-effort vers la file privée d'un utilisateur. Ne lève jamais. */
    private void envoyer(Long utilisateurId, String file, Object charge) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(utilisateurId), file, charge);
        } catch (Exception e) {
            log.warn("Publication temps réel vers l'utilisateur {} impossible : {}",
                    utilisateurId, e.getMessage());
        }
    }
}
