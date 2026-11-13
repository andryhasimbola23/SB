package com.auximad.gestion_immobilisation.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloc D du plan : ajout et consultation d'immobilisations.
 */
class ImmobilisationIntegrationTest extends AbstractIntegrationTest {

    private static final String FORMAT_CODE =
            "^[A-Z0-9]+/\\d{2}/[A-Z0-9]+/[A-Z0-9]+/[A-Z0-9_]+/\\d{3}$";

    @Test
    @DisplayName("Création valide : code unique généré, statut ACTIF, QR absent")
    void creationValide() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        JsonNode cree = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Ordinateur portable");

        org.junit.jupiter.api.Assertions.assertTrue(
                cree.get("codeUnique").asText().matches(FORMAT_CODE),
                "Code inattendu : " + cree.get("codeUnique").asText());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIF", cree.get("statut").asText());
        org.junit.jupiter.api.Assertions.assertTrue(cree.get("qrCode").isNull());
        org.junit.jupiter.api.Assertions.assertEquals("Service Test A", cree.get("serviceNom").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Agence Test A", cree.get("agenceNom").asText());
        org.junit.jupiter.api.Assertions.assertEquals("2183", cree.get("categorieCode").asText());
    }

    @Test
    @DisplayName("Deux créations successives : numéros distincts et croissants")
    void deuxCreationsSansCollision() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        String premier = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Poste fixe 1")
                .get("codeUnique").asText();
        String second = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Poste fixe 2")
                .get("codeUnique").asText();

        org.junit.jupiter.api.Assertions.assertNotEquals(premier, second);
        org.junit.jupiter.api.Assertions.assertTrue(premier.endsWith("/001"), premier);
        org.junit.jupiter.api.Assertions.assertTrue(second.endsWith("/002"), second);
    }

    @Test
    @DisplayName("Champs obligatoires manquants : 400 avec le détail par champ")
    void champsObligatoiresManquants() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());

        postJson("/api/immobilisations", tokenAdmin, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.designation").exists())
                .andExpect(jsonPath("$.errors.dateAcquisition").exists())
                .andExpect(jsonPath("$.errors.valeurAchat").exists())
                .andExpect(jsonPath("$.errors.serviceId").exists())
                .andExpect(jsonPath("$.errors.categorieId").exists());
    }

    @Test
    @DisplayName("Valeur nulle ou négative : 400")
    void valeurInvalide() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        Map<String, Object> corps = payloadValide("Valeur invalide");
        corps.put("valeurAchat", 0);

        postJson("/api/immobilisations", tokenAdmin, corps)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.valeurAchat").exists());
    }

    @Test
    @DisplayName("Taux supérieur à 100 % : 400")
    void tauxInvalide() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        Map<String, Object> corps = payloadValide("Taux invalide");
        corps.put("tauxAmortissement", 150);

        postJson("/api/immobilisations", tokenAdmin, corps)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tauxAmortissement").exists());
    }

    @Test
    @DisplayName("Création par un EMPLOYE : 403")
    void creationParEmployeRefusee() throws Exception {
        String tokenEmploye = tokenDe(employeA.getNomUtilisateur());

        postJson("/api/immobilisations", tokenEmploye, payloadValide("Interdit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Création anonyme : 401")
    void creationAnonymeRefusee() throws Exception {
        postJson("/api/immobilisations", null, payloadValide("Anonyme"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Lecture par code : 200 dans le périmètre, 404 hors périmètre")
    void lectureParCodeRespecteLePerimetre() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bienB = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien du service B");
        String code = bienB.get("codeUnique").asText();

        getJson("/api/immobilisations/code?code=" + code, tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeUnique").value(code));

        String tokenEmployeA = tokenDe(employeA.getNomUtilisateur());
        getJson("/api/immobilisations/code?code=" + code, tokenEmployeA)
                .andExpect(status().isNotFound());

        getJson("/api/immobilisations/" + bienB.get("id").asLong(), tokenEmployeA)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Liste filtrée : un employé ne voit que les biens de son service")
    void listeFiltreeParService() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien visible A");
        creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien invisible B");

        String tokenEmployeA = tokenDe(employeA.getNomUtilisateur());
        getJson("/api/immobilisations", tokenEmployeA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].designation").value("Bien visible A"));

        getJson("/api/immobilisations", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Génération du QR : URL renseignée et fichier réellement écrit")
    void generationQrCode() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien avec QR");
        String code = bien.get("codeUnique").asText();

        postJson("/api/immobilisations/" + bien.get("id").asLong() + "/generer-qr", tokenAdmin, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCode").value("/uploads/qrcodes/"
                        + code.replace("/", "_") + ".png"));

        Path fichier = Path.of("target/test-uploads/qrcodes", code.replace("/", "_") + ".png");
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(fichier),
                "Le fichier QR devrait exister : " + fichier.toAbsolutePath());
    }

    @Test
    @DisplayName("Génération de QR par un EMPLOYE : 403, la route est réservée à l'ADMIN")
    void qrParEmployeRefuse() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien QR protégé");

        String tokenEmployeA = tokenDe(employeA.getNomUtilisateur());
        postJson("/api/immobilisations/" + bien.get("id").asLong() + "/generer-qr", tokenEmployeA, Map.of())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Plan d'amortissement : cinq lignes pour un taux de 20 %")
    void planAmortissement() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien amortissable");

        getJson("/api/immobilisations/" + bien.get("id").asLong() + "/amortissements", tokenAdmin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dureeAmortissement").value(5))
                .andExpect(jsonPath("$.lignes", hasSize(5)))
                .andExpect(jsonPath("$.vncActuelle").isNumber());
    }

    @Test
    @DisplayName("Mise à jour : la fiche change, le code unique est conservé")
    void miseAJourConserveLeCode() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Ancienne désignation");
        String code = bien.get("codeUnique").asText();

        Map<String, Object> corps = payloadValide("Nouvelle désignation");

        putJson("/api/immobilisations/" + bien.get("id").asLong(), tokenAdmin, corps)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("Nouvelle désignation"))
                .andExpect(jsonPath("$.codeUnique").value(code));
    }

    @Test
    @DisplayName("Suppression d'un bien sans référence : 204")
    void suppressionBienSansReference() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien jetable");

        deleteJson("/api/immobilisations/" + bien.get("id").asLong(), tokenAdmin)
                .andExpect(status().isNoContent());

        getJson("/api/immobilisations/" + bien.get("id").asLong(), tokenAdmin)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Le code généré respecte le format agence/année/service/catégorie/type/numéro")
    void formatDuCode() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Contrôle du format");

        org.junit.jupiter.api.Assertions.assertTrue(
                bien.get("codeUnique").asText().matches(FORMAT_CODE),
                "Code inattendu : " + bien.get("codeUnique").asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                bien.get("codeUnique").asText().startsWith("AGTA/"),
                bien.get("codeUnique").asText());
    }

    private Map<String, Object> payloadValide(String designation) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("designation", designation);
        corps.put("dateAcquisition", "2024-01-15T00:00:00");
        corps.put("dateMiseService", "2024-02-01T00:00:00");
        corps.put("valeurAchat", 1500000);
        corps.put("etatPhysique", "BON");
        corps.put("marque", "Dell");
        corps.put("tauxAmortissement", 20);
        corps.put("typeMateriel", "UCB");
        corps.put("serviceId", serviceA.getId());
        corps.put("agenceId", agenceA.getId());
        corps.put("categorieId", categorie.getId());
        return corps;
    }
}
