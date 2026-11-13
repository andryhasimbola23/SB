package com.auximad.gestion_immobilisation.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloc F du plan : matrice de sécurité et non-divulgation.
 *
 * <p>Ces tests ne vérifient pas le métier mais la barrière : ce qui doit être
 * fermé l'est (401 sans jeton, 403 avec un rôle insuffisant), et un refus ne
 * doit jamais fuiter une donnée d'un autre périmètre.</p>
 */
class SecuritePermissionsIntegrationTest extends AbstractIntegrationTest {

    // ============================================================
    // Anonyme : 401 (et non 403, pour que le client purge sa session)
    // ============================================================

    @Test
    @DisplayName("Anonyme sur toutes les familles de routes : 401")
    void anonymeRefusePartout() throws Exception {
        String[] routes = {
                "/api/immobilisations",
                "/api/mouvements",
                "/api/demandes",
                "/api/notifications",
                "/api/dashboard/stats",
                "/api/agences",
                "/api/services",
                "/api/categories",
                "/api/fournisseurs",
                "/api/utilisateurs",
                "/api/journal-audit",
                "/api/inventaires/campagnes",
                "/api/export/immobilisations/excel"
        };

        for (String route : routes) {
            getJson(route, null)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Anonyme sur une écriture : 401, aucune création")
    void anonymeEcritureRefusee() throws Exception {
        postJson("/api/agences", null, Map.of("codeAgence", "ZZZ", "nomAgence", "Intrus"))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertFalse(
                agenceRepository.findByCodeAgence("ZZZ").isPresent());
    }

    // ============================================================
    // Employé : 403 sur les écritures réservées à l'ADMIN
    // ============================================================

    @Test
    @DisplayName("Employé sur les écritures ADMIN : 403")
    void employeEcrituresAdminRefusees() throws Exception {
        String token = tokenDe(employeA.getNomUtilisateur());

        postJson("/api/agences", token, Map.of("codeAgence", "ZZZ", "nomAgence", "Interdit"))
                .andExpect(status().isForbidden());
        putJson("/api/agences/" + agenceA.getId(), token,
                Map.of("codeAgence", "AGTA", "nomAgence", "Renommée"))
                .andExpect(status().isForbidden());
        deleteJson("/api/agences/" + agenceA.getId(), token)
                .andExpect(status().isForbidden());

        postJson("/api/services", token,
                Map.of("code", "ZZZ", "nomService", "Interdit", "agenceId", agenceA.getId()))
                .andExpect(status().isForbidden());
        postJson("/api/categories", token,
                Map.of("codeComptable", "9999", "nomCategorie", "Interdit", "typeImmobilisation", "COR"))
                .andExpect(status().isForbidden());
        postJson("/api/fournisseurs", token, Map.of("nomFournisseur", "Interdit"))
                .andExpect(status().isForbidden());
        postJson("/api/mouvements", token, Map.of("typeMouvement", "ENTREE"))
                .andExpect(status().isForbidden());
        postJson("/api/inventaires/campagnes", token,
                Map.of("libelle", "Interdite", "dateDebut", "2026-01-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Employé sur les lectures ADMIN : 403")
    void employeLecturesAdminRefusees() throws Exception {
        String token = tokenDe(employeA.getNomUtilisateur());

        getJson("/api/utilisateurs", token).andExpect(status().isForbidden());
        getJson("/api/journal-audit", token).andExpect(status().isForbidden());
        getJson("/api/inventaires/campagnes", token).andExpect(status().isForbidden());
        getJson("/api/export/inventaires/1/excel", token).andExpect(status().isForbidden());
        getJson("/api/demandes/candidats", token).andExpect(status().isForbidden());
    }

    // ============================================================
    // Périmètre de lecture
    // ============================================================

    @Test
    @DisplayName("Bien d'un autre service : lecture unitaire 404, jamais 403 qui confirmerait son existence")
    void bienHorsPerimetreInvisible() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bienB = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien du service B");

        String tokenA = tokenDe(employeA.getNomUtilisateur());
        getJson("/api/immobilisations/" + bienB.get("id").asLong(), tokenA)
                .andExpect(status().isNotFound());
        getJson("/api/immobilisations/code?code=" + bienB.get("codeUnique").asText(), tokenA)
                .andExpect(status().isNotFound());

        // Les mouvements du bien sont invisibles par le même périmètre : la
        // liste est vide, indiscernable d'un bien sans mouvement (aucune fuite).
        getJson("/api/mouvements/immobilisation/" + bienB.get("id").asLong(), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("Notification d'autrui : 404 identique à une notification inexistante")
    void notificationAutruiInvisible() throws Exception {
        String tokenA = tokenDe(employeA.getNomUtilisateur());
        creerDemande(tokenA, "Demande qui notifie l'ADMIN");

        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode notifications = corps(getJson("/api/notifications", tokenAdmin)
                .andExpect(status().isOk())
                .andReturn());
        long notificationAdmin = notifications.get(0).get("id").asLong();

        // L'employé ne peut ni la lire ni la marquer comme lue.
        putJson("/api/notifications/" + notificationAdmin + "/lue", tokenA, Map.of())
                .andExpect(status().isNotFound());

        getJson("/api/notifications", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(notificationAdmin))));
    }

    // ============================================================
    // CORS et non-divulgation
    // ============================================================

    @Test
    @DisplayName("Préflight CORS : autorisé pour l'origine du front, sans authentification")
    void preflightCorsAutorise() throws Exception {
        mockMvc.perform(options("/api/immobilisations")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("Aucune réponse de compte ne contient de mot de passe")
    void motDePasseJamaisExpose() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        getJson("/api/utilisateurs", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].motDePasse").doesNotExist());

        getJson("/api/utilisateurs/" + admin.getId(), tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motDePasse").doesNotExist());

        getJson("/api/auth/me", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }
}
