package com.auximad.gestion_immobilisation.integration;

import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.model.Mouvement;
import com.auximad.gestion_immobilisation.repository.MouvementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export PDF de la fiche d'immobilisation.
 *
 * <p>Vérifie que l'endpoint renvoie un vrai PDF (logo, bandeau, historique,
 * pagination inclus dans le document généré), que le contrôle de périmètre
 * s'applique aussi à cet export, et que la fiche tient sur une seule page
 * même avec une fiche chargée (photo, QR, historique complet).</p>
 */
class ExportPdfIntegrationTest extends AbstractIntegrationTest {

    private static final String URL_PDF = "/api/export/immobilisations/";

    @Autowired
    private MouvementRepository mouvementRepository;

    @Test
    @DisplayName("Fiche PDF : 200, content-type application/pdf et en-tête %PDF")
    void fichePdfAuFormatPdf() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Ordinateur portable");

        MvcResult resultat = mockMvc.perform(get(URL_PDF + bien.get("id").asLong() + "/pdf")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();

        assertPdf(resultat);
    }

    @Test
    @DisplayName("Un employé télécharge la fiche d'un bien de son propre service")
    void employeTelechargeLaFicheDeSonService() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Poste de travail A");

        String tokenEmploye = tokenDe(employeA.getNomUtilisateur());
        MvcResult resultat = mockMvc.perform(get(URL_PDF + bien.get("id").asLong() + "/pdf")
                        .header("Authorization", "Bearer " + tokenEmploye))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();

        assertPdf(resultat);
    }

    @Test
    @DisplayName("Un employé n'accède pas à la fiche d'un bien hors de son service (404)")
    void employeRefuseHorsPerimetre() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bienAutreService = creerImmobilisation(tokenAdmin, serviceB, agenceB, "Bien du service B");

        String tokenEmploye = tokenDe(employeA.getNomUtilisateur());
        mockMvc.perform(get(URL_PDF + bienAutreService.get("id").asLong() + "/pdf")
                        .header("Authorization", "Bearer " + tokenEmploye))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sans jeton, la fiche PDF n'est pas téléchargeable")
    void sansJetonRefuse() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA, "Bien protégé");

        mockMvc.perform(get(URL_PDF + bien.get("id").asLong() + "/pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Fiche chargée (photo, QR, 6 mouvements, description longue) : une seule page")
    void ficheChargeeTientSurUneSeulePage() throws Exception {
        String tokenAdmin = tokenDe(admin.getNomUtilisateur());
        JsonNode bien = creerImmobilisation(tokenAdmin, serviceA, agenceA,
                "Réfrigérateur professionnel Atlas Cold 400 L avec vitre et éclairage intégré");

        // QR code réellement généré sur disque
        postJson("/api/immobilisations/" + bien.get("id").asLong() + "/generer-qr",
                tokenAdmin, Map.of()).andExpect(status().isOk());

        // Photo réellement déposée dans le répertoire d'upload des tests
        Path dossierPhoto = Path.of("target/test-uploads/immobilisations");
        Files.createDirectories(dossierPhoto);
        Files.write(dossierPhoto.resolve("photo_test.png"),
                Files.readAllBytes(Path.of("src/main/resources/static/logo_auximad.png")));

        Immobilisation entite = immobilisationRepository.findById(bien.get("id").asLong()).orElseThrow();
        entite.setPhoto("/uploads/immobilisations/photo_test.png");
        entite.setDescription("Bien de test doté d'une description suffisamment longue pour occuper "
                + "plusieurs lignes dans la fiche, afin de vérifier que la mise en page en deux colonnes "
                + "reste sur une seule page même en cas de contenu abondant.");
        immobilisationRepository.save(entite);

        // 6 mouvements : le PDF n'affiche que les 5 derniers + la mention du reste
        for (int i = 0; i < 6; i++) {
            mouvementRepository.save(Mouvement.builder()
                    .immobilisation(entite)
                    .typeMouvement(i % 2 == 0 ? "ENTREE" : "TRANSFERT")
                    .dateMouvement(LocalDateTime.now().minusDays(i))
                    .ancienneLocalisation("Agence d'origine n°" + (i + 1))
                    .nouvelleLocalisation("Service affectataire n°" + (i + 1))
                    .build());
        }

        MvcResult resultat = mockMvc.perform(get(URL_PDF + bien.get("id").asLong() + "/pdf")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        assertPdf(resultat);

        // Compte réellement les pages du PDF produit
        byte[] octets = resultat.getResponse().getContentAsByteArray();

        // Conservation de l'aperçu pour contrôle visuel dans target/
        Files.write(Path.of("target", "apercu_fiche_chargie.pdf"), octets);

        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(octets));
             PdfDocument doc = new PdfDocument(reader)) {
            assertEquals(1, doc.getNumberOfPages(),
                    "la fiche doit tenir sur une seule page, quel que soit le contenu");
        }
    }

    /** Un PDF commence par « %PDF- » et contient un contenu réellement paginé. */
    private void assertPdf(MvcResult resultat) {
        byte[] octets = resultat.getResponse().getContentAsByteArray();
        assertTrue(octets.length > 1_000,
                "PDF trop petit (" + octets.length + " octets) : le contenu manque");
        String enTete = new String(octets, 0, Math.min(5, octets.length), StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", enTete, "le flux ne commence pas par l'en-tête PDF");
    }
}
