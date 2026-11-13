package com.auximad.gestion_immobilisation.integration;

import com.auximad.gestion_immobilisation.model.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloc A du plan : création et gestion des comptes par l'ADMIN.
 *
 * <p>Il n'existe pas d'inscription publique : le seul chemin de création est
 * {@code POST /api/utilisateurs}, réservé à l'ADMIN.</p>
 */
class UtilisateurIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Création d'un employé : 201, mot de passe jamais renvoyé, BCrypt en base")
    void creationEmployeValide() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload("nouvel.employe", "nouvel.employe@auximad.mg", "Nina", "Nouvelle",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nomUtilisateur").value("nouvel.employe"))
                .andExpect(jsonPath("$.role").value("EMPLOYE"))
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.serviceId").value(serviceA.getId().intValue()))
                .andExpect(jsonPath("$.serviceNom").value("Service Test A"))
                .andExpect(jsonPath("$.motDePasse").value(nullValue()));

        Utilisateur cree = utilisateurRepository.findByNomUtilisateur("nouvel.employe").orElseThrow();
        assertNotEquals(MOT_DE_PASSE, cree.getMotDePasse());
        assertTrue(cree.getMotDePasse().startsWith("$2"), "Le mot de passe doit être haché en BCrypt");

        // Le compte créé peut immédiatement se connecter.
        tokenDe("nouvel.employe", MOT_DE_PASSE);
    }

    @Test
    @DisplayName("Nom d'utilisateur déjà pris : 400 explicite, aucune création")
    void nomUtilisateurDejaPris() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload(employeA.getNomUtilisateur(), "autre.email@auximad.mg", "Autre", "Compte",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("déjà utilisé")));
    }

    @Test
    @DisplayName("Email déjà pris : 400 explicite")
    void emailDejaPris() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload("autre.compte", employeA.getEmail(), "Autre", "Compte",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("déjà utilisé")));
    }

    @Test
    @DisplayName("Champs obligatoires manquants : 400 avec le détail par champ")
    void champsObligatoiresManquants() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nomUtilisateur").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.nom").exists())
                .andExpect(jsonPath("$.errors.prenom").exists())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    @DisplayName("Email invalide : 400")
    void emailInvalide() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload("email.invalide", "pas-un-email", "Test", "Email",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("Mot de passe trop court : 400")
    void motDePasseTropCourt() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload("mot.passe.court", "court@auximad.mg", "Test", "Court",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.motDePasse").exists());
    }

    @Test
    @DisplayName("Rôle inconnu : 400, jamais 500")
    void roleInconnu() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload("role.inconnu", "role.inconnu@auximad.mg", "Test", "Role",
                        "SUPER_ADMIN", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Service inexistant : 404")
    void serviceInexistant() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenAdmin,
                payload("service.absent", "service.absent@auximad.mg", "Test", "Service",
                        "EMPLOYE", 999_999L, agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Création par un EMPLOYE : 403")
    void creationParEmployeRefusee() throws Exception {
        String tokenEmploye = tokenDe(employeA.getNomUtilisateur());

        postJson("/api/utilisateurs", tokenEmploye,
                payload("interdit", "interdit@auximad.mg", "Test", "Interdit",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Création anonyme : 401")
    void creationAnonymeRefusee() throws Exception {
        postJson("/api/utilisateurs", null,
                payload("anonyme", "anonyme@auximad.mg", "Test", "Anonyme",
                        "EMPLOYE", serviceA.getId(), agenceA.getId(), MOT_DE_PASSE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Recherche insensible à la casse sur prénom, nom, identifiant et email")
    void rechercheUtilisateurs() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        getJson("/api/utilisateurs/search?keyword=ALICE", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nomUtilisateur", hasItem("employe.a")));

        getJson("/api/utilisateurs/search?keyword=auximad.mg", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nomUtilisateur", hasItem("admin.it")));
    }

    @Test
    @DisplayName("Mise à jour : les coordonnées changent, le mot de passe non fourni est conservé")
    void miseAJourConserveLeMotDePasse() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        Map<String, Object> corps = payload(employeA.getNomUtilisateur(), employeA.getEmail(),
                "Alice", "Employe", "EMPLOYE", serviceA.getId(), agenceA.getId(), null);
        corps.put("telephone", "0340000000");

        putJson("/api/utilisateurs/" + employeA.getId(), tokenAdmin, corps)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("0340000000"))
                .andExpect(jsonPath("$.motDePasse").value(nullValue()));

        // L'ancien mot de passe fonctionne toujours : aucun écrasement silencieux.
        tokenDe(employeA.getNomUtilisateur(), MOT_DE_PASSE);
    }

    @Test
    @DisplayName("Suppression de son propre compte : 400")
    void suppressionDeSonPropreCompteRefusee() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        deleteJson("/api/utilisateurs/" + admin.getId(), tokenAdmin)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Suppression d'un compte référencé : 409 invitant à désactiver")
    void suppressionCompteReferenceeRefusee() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        String tokenEmploye = tokenDe(employeA.getNomUtilisateur());
        creerDemande(tokenEmploye, "Demande qui référence le compte");

        deleteJson("/api/utilisateurs/" + employeA.getId(), tokenAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Désactivez-le")));
    }

    private Map<String, Object> payload(String nomUtilisateur, String email, String prenom, String nom,
                                        String role, Long serviceId, Long agenceId, String motDePasse) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("nomUtilisateur", nomUtilisateur);
        corps.put("email", email);
        corps.put("nom", nom);
        corps.put("prenom", prenom);
        corps.put("role", role);
        corps.put("actif", true);
        corps.put("serviceId", serviceId);
        corps.put("agenceId", agenceId);
        if (motDePasse != null) {
            corps.put("motDePasse", motDePasse);
        }
        return corps;
    }
}
