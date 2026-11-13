package com.auximad.gestion_immobilisation.integration;

import com.auximad.gestion_immobilisation.dto.DemandeTempsReelDto;
import com.auximad.gestion_immobilisation.dto.NotificationTempsReelDto;
import com.auximad.gestion_immobilisation.service.NotificationMetier;
import com.auximad.gestion_immobilisation.service.RealtimeMetier;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Temps réel de bout en bout : chaque événement métier pousse le bon message,
 * vers la bonne file, vers le seul utilisateur concerné — et le handshake
 * refuse sans jeton.
 *
 * {@code SimpMessagingTemplate} est remplacé par un mock : on vérifie le
 * routage (qui reçoit quoi) sans ouvrir de vraie socket, le broker réel étant
 * couvert par le démarrage du contexte et les heart-beats configurés.
 */
class TempsReelIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private SimpMessagingTemplate brokerMessagingTemplate;

    // ============================================================
    // Employé → Administrateur : dépôt d'une demande
    // ============================================================

    @Test
    @DisplayName("Dépôt : l'admin reçoit notification + événement de liste, le demandeur son événement")
    void depotPublieAuxAdminsEtAuDemandeur() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());

        JsonNode demande = creerDemande(tokenA, "Ordinateur pour le temps réel");
        long demandeId = demande.get("id").asLong();
        String idAdmin = String.valueOf(admin.getId());
        String idA = String.valueOf(employeA.getId());

        // L'admin : la notification (cloche + compteur)…
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(idAdmin), eq(RealtimeMetier.FILE_NOTIFICATIONS),
                argThat(charge -> charge instanceof NotificationTempsReelDto evt
                        && evt.notification() != null
                        && NotificationMetier.NOUVELLE_DEMANDE.equals(evt.notification().getType())
                        && evt.nonLues() >= 1));

        // …et l'événement de rafraîchissement de sa liste.
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(idAdmin), eq(RealtimeMetier.FILE_DEMANDES),
                argThat(charge -> charge instanceof DemandeTempsReelDto evt
                        && RealtimeMetier.EVT_NOUVELLE_DEMANDE.equals(evt.evenement())
                        && evt.demandeId() == demandeId));

        // Le demandeur voit aussi sa nouvelle demande apparaître (ses sessions).
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(idA), eq(RealtimeMetier.FILE_DEMANDES),
                argThat(charge -> charge instanceof DemandeTempsReelDto evt
                        && RealtimeMetier.EVT_NOUVELLE_DEMANDE.equals(evt.evenement())
                        && evt.demandeId() == demandeId));

        // Un employé hors demande ni destinataire ne reçoit rien.
        String idB = String.valueOf(employeB.getId());
        verify(brokerMessagingTemplate, never())
                .convertAndSendToUser(eq(idB), anyString(), any());
    }

    // ============================================================
    // Administrateur → Employé : décision
    // ============================================================

    @Test
    @DisplayName("Rejet : seul le demandeur est notifié et voit l'événement de statut")
    void rejetPublieAuSeulDemandeur() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande à rejeter");

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("decision", "REJETTEE");
        corps.put("commentaire", "Budget insuffisant");
        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin, corps)
                .andExpect(status().isOk());

        String idA = String.valueOf(employeA.getId());
        long demandeId = demande.get("id").asLong();

        // Notification de rejet pour le demandeur…
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(idA), eq(RealtimeMetier.FILE_NOTIFICATIONS),
                argThat(charge -> charge instanceof NotificationTempsReelDto evt
                        && evt.notification() != null
                        && NotificationMetier.DEMANDE_REJETEE.equals(evt.notification().getType())));

        // …et événement de liste pour rafraîchir son statut (Acceptée/Rejetée).
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(idA), eq(RealtimeMetier.FILE_DEMANDES),
                argThat(charge -> charge instanceof DemandeTempsReelDto evt
                        && RealtimeMetier.EVT_DEMANDE_DECIDEE.equals(evt.evenement())
                        && evt.demandeId() == demandeId));

        // L'admin qui a décidé reçoit l'événement de liste (ses sessions se
        // resynchronisent), mais jamais la notification de sa propre décision.
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(String.valueOf(admin.getId())), eq(RealtimeMetier.FILE_DEMANDES),
                argThat(charge -> charge instanceof DemandeTempsReelDto evt
                        && RealtimeMetier.EVT_DEMANDE_DECIDEE.equals(evt.evenement())));

        // L'employé B n'a rien à voir dans cette décision.
        verify(brokerMessagingTemplate, never())
                .convertAndSendToUser(eq(String.valueOf(employeB.getId())), anyString(), any());
    }

    @Test
    @DisplayName("Approbation : le demandeur reçoit DEMANDE_APPROUVEE")
    void approbationPublieeAuDemandeur() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande à approuver");

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("decision", "APPROUVEE");
        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin, corps)
                .andExpect(status().isOk());

        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(String.valueOf(employeA.getId())), eq(RealtimeMetier.FILE_NOTIFICATIONS),
                argThat(charge -> charge instanceof NotificationTempsReelDto evt
                        && evt.notification() != null
                        && NotificationMetier.DEMANDE_APPROUVEE.equals(evt.notification().getType())));
    }

    // ============================================================
    // Annulation : événement de liste, sans notification
    // ============================================================

    @Test
    @DisplayName("Annulation : les admins rechargent leur liste, sans notification")
    void annulationPublieeAuxAdmins() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande à annuler");
        long demandeId = demande.get("id").asLong();

        patchJson("/api/demandes/" + demandeId + "/annuler", tokenA, Map.of())
                .andExpect(status().isOk());

        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(String.valueOf(admin.getId())), eq(RealtimeMetier.FILE_DEMANDES),
                argThat(charge -> charge instanceof DemandeTempsReelDto evt
                        && RealtimeMetier.EVT_DEMANDE_ANNULEE.equals(evt.evenement())
                        && evt.demandeId() == demandeId));

        // Une annulation n'est pas une décision : aucune notification poussée.
        verify(brokerMessagingTemplate, never()).convertAndSendToUser(
                eq(String.valueOf(admin.getId())), eq(RealtimeMetier.FILE_NOTIFICATIONS),
                argThat(charge -> charge instanceof NotificationTempsReelDto evt
                        && evt.notification() != null
                        && evt.notification().getType().startsWith("DEMANDE_")));
    }

    // ============================================================
    // Sécurité du handshake
    // ============================================================

    @Test
    @DisplayName("Handshake : sans jeton, 401 — aucune socket ne s'ouvre")
    void handshakeSansJetonRefuse() throws Exception {
        mockMvc.perform(get("/ws"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Handshake : avec un jeton valide, la porte passe (refus autre que 401)")
    void handshakeAvecJetonAccepte() throws Exception {
        String token = tokenDe(admin.getNomUtilisateur());

        // MockMvc ne réalise pas l'upgrade WebSocket (400 « headers absents ») :
        // on vérifie ici que le jeton valide franchit l'intercepteur — ce qui
        // le distinguerait d'un 401.
        mockMvc.perform(get("/ws").param("token", token))
                .andExpect(result -> assertNotEquals(401,
                        result.getResponse().getStatus(),
                        "un jeton valide ne doit pas être refusé par l'intercepteur"));
    }

    @Test
    @DisplayName("Handshake : un jeton périmé est refusé comme sans jeton")
    void handshakeJetonInvalideRefuse() throws Exception {
        mockMvc.perform(get("/ws").param("token", "n-importe-quoi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Le compteur de non-lues publié suit la base")
    void compteurPublieAligneSurLaBase() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        creerDemande(tokenA, "Première demande");
        creerDemande(tokenA, "Seconde demande");

        // Deux notifications pour l'admin : le dernier compteur publié vaut 2.
        verify(brokerMessagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(String.valueOf(admin.getId())), eq(RealtimeMetier.FILE_NOTIFICATIONS),
                argThat(charge -> charge instanceof NotificationTempsReelDto evt
                        && evt.nonLues() == 2));
        assertEquals(2, notificationCompteurPourAdmin());
    }

    private long notificationCompteurPourAdmin() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE lue = FALSE", Long.class);
    }
}
