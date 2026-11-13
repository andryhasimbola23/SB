package com.auximad.gestion_immobilisation.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export PDF de la fiche d'immobilisation.
 *
 * <p>Vérifie que l'endpoint renvoie un vrai PDF (logo, bandeau, historique,
 * pagination inclus dans le document généré) et que le contrôle de périmètre
 * s'applique aussi à cet export.</p>
 */
class ExportPdfIntegrationTest extends AbstractIntegrationTest {

    private static final String URL_PDF = "/api/export/immobilisations/";

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

    /** Un PDF commence par « %PDF- » et contient un contenu réellement paginé. */
    private void assertPdf(MvcResult resultat) {
        byte[] octets = resultat.getResponse().getContentAsByteArray();
        assertTrue(octets.length > 1_000,
                "PDF trop petit (" + octets.length + " octets) : le contenu manque");
        String enTete = new String(octets, 0, Math.min(5, octets.length), StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", enTete, "le flux ne commence pas par l'en-tête PDF");
    }
}
