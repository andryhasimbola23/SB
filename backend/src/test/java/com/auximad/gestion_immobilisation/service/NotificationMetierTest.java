package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.NotificationDto;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Notification;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationMetierTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private NotificationMetier notificationMetier;

    private Utilisateur admin(long id) {
        return Utilisateur.builder().id(id).role(Role.ADMIN).actif(true).build();
    }

    @Test
    @DisplayName("notifierAdmins : chaque admin actif reçoit une notification, sauf l'acteur")
    void notifierAdminsSaufActeur() {
        Utilisateur a2 = admin(2L);
        Utilisateur a3 = admin(3L);
        when(utilisateurRepository.findByRole(Role.ADMIN))
                .thenReturn(List.of(admin(1L), a2, a3));
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(a2));
        when(utilisateurRepository.findById(3L)).thenReturn(Optional.of(a3));

        notificationMetier.notifierAdmins(
                NotificationMetier.NOUVELLE_DEMANDE, "Titre", "Message", "/demandes", 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Long> destinataires = captor.getAllValues().stream()
                .map(n -> n.getDestinataire().getId())
                .toList();
        assertTrue(destinataires.contains(2L) && destinataires.contains(3L),
                "les deux autres admins doivent être notifiés");
        assertFalse(destinataires.contains(1L), "l'acteur ne reçoit jamais sa propre action");
        verify(utilisateurRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("notifier : un destinataire inactif est ignoré silencieusement")
    void destinataireInactifIgnore() {
        Utilisateur inactif = Utilisateur.builder().id(7L).role(Role.EMPLOYE).actif(false).build();
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(inactif));

        notificationMetier.notifier(7L, "T", "Titre", "Message", null, null);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifier : l'acteur ne reçoit jamais de notification sur sa propre action")
    void autoNotificationBloquee() {
        notificationMetier.notifier(5L, "T", "Titre", "Message", null, 5L);

        verify(utilisateurRepository, never()).findById(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("marquerLue : seul le destinataire peut marquer sa notification — 404 sinon")
    void marquerLueParAutruiInterdit() {
        Notification notification = Notification.builder()
                .id(9L).destinataire(admin(2L)).lue(false).build();
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));

        assertThrows(ResourceNotFoundException.class,
                () -> notificationMetier.marquerLue(9L, 1L));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("marquerLue : le destinataire valide passe la notification en lue avec horodatage")
    void marquerLueMetAJour() {
        Notification notification = Notification.builder()
                .id(9L).destinataire(admin(2L)).lue(false).build();
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto dto = notificationMetier.marquerLue(9L, 2L);

        assertTrue(dto.isLue(), "la notification doit être marquée lue");
        assertNotNull(dto.getDateLue(), "l'horodatage de lecture doit être posé");
        verify(notificationRepository).save(notification);
    }
}
