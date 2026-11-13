package com.auximad.gestion_immobilisation.integration;

import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloc E du plan : cœur du parcours — dépôt, décision, livraison réelle,
 * mouvement, notification, audit et garde-fous.
 */
class DemandeWorkflowIntegrationTest extends AbstractIntegrationTest {

    // ============================================================
    // Dépôt et périmètre
    // ============================================================

    @Test
    @DisplayName("Dépôt : demande EN_ATTENTE, service figé et référence calculée")
    void depotDemande() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());

        JsonNode demande = creerDemande(tokenA, "Ordinateur portable pour Alice");

        assertEquals("EN_ATTENTE", demande.get("statut").asText());
        assertEquals(employeA.getId().longValue(), demande.get("demandeurId").asLong());
        assertEquals(serviceA.getId().longValue(), demande.get("serviceId").asLong());
        assertEquals("Service Test A", demande.get("serviceNom").asText());
        assertTrue(demande.get("reference").asText().matches("^DEM-\\d{4}-\\d{4,}$"),
                "Référence inattendue : " + demande.get("reference").asText());
    }

    @Test
    @DisplayName("Dépôt : priorité URGENTE et quantité reprises")
    void depotUrgent() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("designation", "Deux écrans urgents");
        corps.put("motif", "Équipe projet");
        corps.put("quantite", 2);
        corps.put("priorite", "URGENTE");
        corps.put("categorieId", categorie.getId());

        postJson("/api/demandes", tokenA, corps)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantite").value(2))
                .andExpect(jsonPath("$.priorite").value("URGENTE"));
    }

    @Test
    @DisplayName("Validation : désignation, motif et quantité sont contrôlés")
    void validationDemande() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());

        postJson("/api/demandes", tokenA, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.designation").exists())
                .andExpect(jsonPath("$.errors.motif").exists());

        Map<String, Object> quantiteZero = new LinkedHashMap<>();
        quantiteZero.put("designation", "Quantité invalide");
        quantiteZero.put("motif", "Test");
        quantiteZero.put("quantite", 0);
        postJson("/api/demandes", tokenA, quantiteZero)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantite").exists());
    }

    @Test
    @DisplayName("Périmètre : un employé ne voit et ne lit que ses propres demandes")
    void perimetreDesDemandes() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        String tokenB = tokenDe(employeB.getNomUtilisateur());
        JsonNode demandeA = creerDemande(tokenA, "Demande d'Alice");
        JsonNode demandeB = creerDemande(tokenB, "Demande de Bob");

        getJson("/api/demandes", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(demandeA.get("id").asLong()));

        getJson("/api/demandes/" + demandeB.get("id").asLong(), tokenA)
                .andExpect(status().isNotFound());

        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        getJson("/api/demandes", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Notification : l'ADMIN est prévenu dès le dépôt")
    void notificationAdminAuDepot() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        creerDemande(tokenA, "Demande notifiée");

        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        getJson("/api/notifications", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("NOUVELLE_DEMANDE")));
    }

    // ============================================================
    // Décisions
    // ============================================================

    @Test
    @DisplayName("Refus sans motif : 400 — un non muet n'est pas acceptable")
    void refusSansMotifRefuse() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande à refuser");
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("REJETTEE", null, null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Refus motivé : 200, traité par l'ADMIN et notification à l'employé")
    void refusMotivé() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande à refuser avec motif");
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("REJETTEE", "Budget insuffisant cette année", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJETTEE"))
                .andExpect(jsonPath("$.commentaireTraitement").value("Budget insuffisant cette année"))
                .andExpect(jsonPath("$.traiteeParId").value(admin.getId()));

        getJson("/api/notifications", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("DEMANDE_REJETEE")));
    }

    @Test
    @DisplayName("Approbation sans bien : 200, aucun mouvement")
    void approbationSansBien() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Approbation sans livraison");
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.immobilisationId").value(nullValue()))
                .andExpect(jsonPath("$.mouvementId").value(nullValue()));

        getJson("/api/mouvements", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        getJson("/api/notifications", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("DEMANDE_APPROUVEE")));
    }

    @Test
    @DisplayName("Décision par un EMPLOYE : 403")
    void decisionParEmployeRefusee() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Décision interdite");

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenA,
                decision("APPROUVEE", null, null, null))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Livraison réelle
    // ============================================================

    @Test
    @DisplayName("Livraison d'un bien d'un autre service : TRANSFERT, code réémis, QR invalidé")
    void livraisonParTransfert() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien à transférer");
        long bienId = bien.get("id").asLong();
        String ancienCode = bien.get("codeUnique").asText();

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Besoin du bien du service B");

        JsonNode decidee = corps(patchJson(
                "/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.mouvementType").value("TRANSFERT"))
                .andExpect(jsonPath("$.immobilisationId").value(bienId))
                .andReturn());

        long mouvementId = decidee.get("mouvementId").asLong();
        String nouveauCode = decidee.get("immobilisationCode").asText();
        assertNotEquals(ancienCode, nouveauCode, "Un transfert doit réémettre le code unique");

        getJson("/api/immobilisations/" + bienId, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(serviceA.getId().intValue()))
                .andExpect(jsonPath("$.agenceId").value(agenceA.getId().intValue()))
                .andExpect(jsonPath("$.codeUnique").value(nouveauCode))
                .andExpect(jsonPath("$.qrCode").value(nullValue()));

        // Le bien est désormais dans le périmètre de l'employé, et scannable.
        getJson("/api/immobilisations", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bienId)));
        getJson("/api/immobilisations/code?code=" + nouveauCode, tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bienId));

        getJson("/api/mouvements/" + mouvementId, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMouvement").value("TRANSFERT"))
                .andExpect(jsonPath("$.immobilisationId").value(bienId))
                .andExpect(jsonPath("$.demandeReference").value(demande.get("reference").asText()));
    }

    @Test
    @DisplayName("Livraison d'un bien déjà dans le service : AFFECTATION, code et QR conservés")
    void livraisonParAffectation() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien déjà dans le service A");
        long bienId = bien.get("id").asLong();
        String code = bien.get("codeUnique").asText();

        JsonNode avecQr = corps(postJson("/api/immobilisations/" + bienId + "/generer-qr",
                tokenAdmin, Map.of()).andExpect(status().isOk()).andReturn());
        String qr = avecQr.get("qrCode").asText();

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Besoin d'un bien déjà présent");

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.mouvementType").value("AFFECTATION"))
                .andExpect(jsonPath("$.immobilisationCode").value(code));

        getJson("/api/immobilisations/" + bienId, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeUnique").value(code))
                .andExpect(jsonPath("$.qrCode").value(qr))
                .andExpect(jsonPath("$.serviceId").value(serviceA.getId().intValue()));
    }

    @Test
    @DisplayName("Bien déjà livré sur une autre demande : 400")
    void bienDejaLivreRefuse() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien à livrer une seule fois");
        long bienId = bien.get("id").asLong();

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode premiere = creerDemande(tokenA, "Première demande");
        patchJson("/api/demandes/" + premiere.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, null))
                .andExpect(status().isOk());

        JsonNode seconde = creerDemande(tokenA, "Seconde demande");
        patchJson("/api/demandes/" + seconde.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("déjà été remis")));
    }

    @Test
    @DisplayName("Bien CEDE ou HORS_SERVICE : 400, demande toujours EN_ATTENTE")
    void bienIndisponibleRefuse() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bienCede = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien cédé");
        Immobilisation entite = immobilisationRepository.findById(bienCede.get("id").asLong()).orElseThrow();
        entite.setStatut("CEDE");
        immobilisationRepository.saveAndFlush(entite);

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande sur bien cédé");

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienCede.get("id").asLong(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("statut")));

        getJson("/api/demandes/" + demande.get("id").asLong(), tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    @DisplayName("Employé sans service : la destination doit être choisie explicitement")
    void livraisonEmployeSansService() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien pour employé sans service");
        long bienId = bien.get("id").asLong();

        String tokenSans = tokenDe(employeSansService.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenSans, "Besoin d'un employé non affecté");
        assertTrue(demande.get("serviceId").isNull(),
                "Une demande sans service doit rester sans service");

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("aucun service")));

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, serviceA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.mouvementType").value("TRANSFERT"));
    }

    // ============================================================
    // Annulation et re-décision
    // ============================================================

    @Test
    @DisplayName("Annulation de sa propre demande en attente : 200 ANNULEE")
    void annulationPropreDemande() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande à annuler");

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/annuler", tokenA, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    @Test
    @DisplayName("Annulation de la demande d'autrui : 404")
    void annulationDemandeAutruiRefusee() throws Exception {
        String tokenB = tokenDe(employeB.getNomUtilisateur());
        JsonNode demandeB = creerDemande(tokenB, "Demande de Bob");

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        patchJson("/api/demandes/" + demandeB.get("id").asLong() + "/annuler", tokenA, Map.of())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Annulation d'une demande déjà décidée : 400")
    void annulationApresDecisionRefusee() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande déjà traitée");
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, null, null))
                .andExpect(status().isOk());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/annuler", tokenA, Map.of())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Re-décision d'une demande close : 400")
    void reDecisionRefusee() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande close");
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, null, null))
                .andExpect(status().isOk());

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("REJETTEE", "Trop tard", null, null))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // Audit et candidats
    // ============================================================

    @Test
    @DisplayName("Audit : la décision et la création du bien sont tracées")
    void auditDeLaDecision() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien audité");
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande auditée");

        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bien.get("id").asLong(), null))
                .andExpect(status().isOk());

        getJson("/api/journal-audit?action=DECISION_DEMANDE", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description",
                        containsString(demande.get("reference").asText())));

        getJson("/api/journal-audit?action=CREATION&entite=Immobilisation", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Candidats : un bien livrable est proposé, puis disparaît après livraison")
    void candidatsDeLivraison() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien candidat");
        long bienId = bien.get("id").asLong();

        getJson("/api/demandes/candidats", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bienId)));

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        JsonNode demande = creerDemande(tokenA, "Demande pour candidat");
        patchJson("/api/demandes/" + demande.get("id").asLong() + "/decision", tokenAdmin,
                decision("APPROUVEE", null, bienId, null))
                .andExpect(status().isOk());

        getJson("/api/demandes/candidats", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(hasItem((int) bienId))));
    }

    private Map<String, Object> decision(String decision, String commentaire,
                                         Long immobilisationId, Long destinationServiceId) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("decision", decision);
        if (commentaire != null) {
            corps.put("commentaire", commentaire);
        }
        if (immobilisationId != null) {
            corps.put("immobilisationId", immobilisationId);
        }
        if (destinationServiceId != null) {
            corps.put("destinationServiceId", destinationServiceId);
        }
        return corps;
    }
}
