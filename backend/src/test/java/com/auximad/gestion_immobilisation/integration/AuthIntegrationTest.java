package com.auximad.gestion_immobilisation.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.security.Key;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloc B du plan : connexion, validation de session et refus d'accès.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Value("${jwt.secret}")
    private String secretJwt;

    @Test
    @DisplayName("Login admin : jeton, rôle ADMIN et absence de service")
    void loginAdminReussi() throws Exception {
        postJson("/api/auth/login", null,
                Map.of("nomUtilisateur", admin.getNomUtilisateur(), "motDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.nomUtilisateur").value("admin.it"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.nomComplet").value("Admin Test"))
                .andExpect(jsonPath("$.serviceId").value(nullValue()));
    }

    @Test
    @DisplayName("Login employé : le service rattaché délimite le périmètre")
    void loginEmployeReussi() throws Exception {
        postJson("/api/auth/login", null,
                Map.of("nomUtilisateur", employeA.getNomUtilisateur(), "motDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYE"))
                .andExpect(jsonPath("$.serviceId").value(serviceA.getId().intValue()))
                .andExpect(jsonPath("$.serviceNom").value("Service Test A"));
    }

    @Test
    @DisplayName("Mauvais mot de passe : 401 sans distinction avec un compte inconnu")
    void mauvaisMotDePasseRefuse() throws Exception {
        postJson("/api/auth/login", null,
                Map.of("nomUtilisateur", admin.getNomUtilisateur(), "motDePasse", "MauvaisMotDePasse!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants refusés."));
    }

    @Test
    @DisplayName("Utilisateur inconnu : 401, pas d'énumération des comptes")
    void utilisateurInconnuRefuse() throws Exception {
        postJson("/api/auth/login", null,
                Map.of("nomUtilisateur", "inconnu.du.tout", "motDePasse", MOT_DE_PASSE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants refusés."));
    }

    @Test
    @DisplayName("Compte désactivé : la connexion est refusée")
    void compteDesactiveRefuse() throws Exception {
        employeA.setActif(false);
        utilisateurRepository.saveAndFlush(employeA);

        postJson("/api/auth/login", null,
                Map.of("nomUtilisateur", employeA.getNomUtilisateur(), "motDePasse", MOT_DE_PASSE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Corps de connexion vide : erreur client contrôlée, jamais 500")
    void loginVideNeCassePasLeServeur() throws Exception {
        postJson("/api/auth/login", null, Map.of())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/auth/me : même payload que le login pour le porteur du jeton")
    void meAvecJetonValide() throws Exception {
        String token = tokenDe(employeA.getNomUtilisateur());

        getJson("/api/auth/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeA.getId()))
                .andExpect(jsonPath("$.nomUtilisateur").value("employe.a"))
                .andExpect(jsonPath("$.role").value("EMPLOYE"))
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.serviceId").value(serviceA.getId().intValue()));
    }

    @Test
    @DisplayName("GET /api/auth/me sans jeton : 401")
    void meSansJetonRefuse() throws Exception {
        getJson("/api/auth/me", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Jeton malformé : 401, pas 500")
    void meAvecJetonMalforme() throws Exception {
        getJson("/api/auth/me", "pas-un-vrai-jeton")
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Jeton expiré : 401")
    void meAvecJetonExpire() throws Exception {
        getJson("/api/auth/me", jetonExpire(admin.getNomUtilisateur()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Route protégée sans jeton : 401")
    void routeProtegeeSansJeton() throws Exception {
        getJson("/api/immobilisations", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Route ADMIN avec un jeton EMPLOYE : 403")
    void routeAdminAvecJetonEmploye() throws Exception {
        String token = tokenDe(employeA.getNomUtilisateur());

        getJson("/api/utilisateurs", token)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Route authentifiée avec jeton valide : 200")
    void routeAuthentifieeAvecJeton() throws Exception {
        String token = tokenDe(employeA.getNomUtilisateur());

        getJson("/api/immobilisations", token)
                .andExpect(status().isOk());
    }

    private String jetonExpire(String nomUtilisateur) {
        Key cle = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretJwt));
        return Jwts.builder()
                .setSubject(nomUtilisateur)
                .setIssuedAt(new Date(System.currentTimeMillis() - 7_200_000L))
                .setExpiration(new Date(System.currentTimeMillis() - 3_600_000L))
                .signWith(cle, SignatureAlgorithm.HS256)
                .compact();
    }
}
