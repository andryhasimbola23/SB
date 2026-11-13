package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.NotificationDto;
import com.auximad.gestion_immobilisation.dto.NotificationTempsReelDto;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.NotificationRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Publication temps réel : routage par utilisateur, compteur exact et
 * absorption des pannes de broker (le temps réel ne doit jamais faire échouer
 * une opération métier).
 *
 * Hors transaction (cas de ces tests), la publication est immédiate — le
 * différé après commit est couvert par les tests d'intégration.
 */
@ExtendWith(MockitoExtension.class)
class RealtimeMetierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private RealtimeMetier realtimeMetier;

    @Test
    @DisplayName("Notification : poussée à la file du destinataire avec le compteur exact")
    void publierNotificationVersLeBonUtilisateur() {
        when(notificationRepository.countByDestinataireIdAndLueFalse(7L)).thenReturn(3L);
        NotificationDto notification = new NotificationDto();
        notification.setId(9L);
        notification.setType(NotificationMetier.NOUVELLE_DEMANDE);
        notification.setTitre("Nouvelle demande d'immobilisation");

        realtimeMetier.publierNotification(7L, notification);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("7"), eq(RealtimeMetier.FILE_NOTIFICATIONS), captor.capture());
        NotificationTempsReelDto charge = (NotificationTempsReelDto) captor.getValue();
        assertEquals(3L, charge.nonLues(), "le compteur doit être celui du serveur");
        assertSame(notification, charge.notification());
    }

    @Test
    @DisplayName("Sans destinataire ou sans notification : rien n'est publié")
    void sansDestinataireRienNestPublie() {
        realtimeMetier.publierNotification(null, new NotificationDto());
        realtimeMetier.publierNotification(7L, null);
        realtimeMetier.publierDemandes(null, RealtimeMetier.EVT_NOUVELLE_DEMANDE, 5L);
        realtimeMetier.publierDemandes(7L, null, null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Demandes : chaque admin actif reçoit l'événement, pas les autres")
    void publierDemandesAuxAdminsActifs() {
        Utilisateur actif = Utilisateur.builder().id(1L).role(Role.ADMIN).actif(true).build();
        Utilisateur inactif = Utilisateur.builder().id(2L).role(Role.ADMIN).actif(false).build();
        when(utilisateurRepository.findByRole(Role.ADMIN)).thenReturn(List.of(actif, inactif));

        realtimeMetier.publierDemandesAuxAdmins(RealtimeMetier.EVT_NOUVELLE_DEMANDE, 5L);

        verify(messagingTemplate).convertAndSendToUser(
                eq("1"), eq(RealtimeMetier.FILE_DEMANDES), any());
        verify(messagingTemplate, never())
                .convertAndSendToUser(eq("2"), anyString(), any());
    }

    @Test
    @DisplayName("Broker à terre : l'erreur est absorbée, l'appelant ne voit rien")
    void erreurBrokerAbsorbee() {
        when(notificationRepository.countByDestinataireIdAndLueFalse(7L)).thenReturn(1L);
        doThrow(new RuntimeException("broker injoignable"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());

        assertDoesNotThrow(() -> realtimeMetier.publierNotification(7L, new NotificationDto()));
    }
}
