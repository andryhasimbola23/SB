package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.ExportService;
import com.auximad.gestion_immobilisation.service.PerimetreService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExportController {

    private final ExportService exportService;
    private final PerimetreService perimetreService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ============ EXCEL ============

    @GetMapping("/immobilisations/excel")
    public ResponseEntity<InputStreamResource> exportImmobilisationsExcel(
            @AuthenticationPrincipal Utilisateur currentUser) {
        ByteArrayInputStream stream = exportService
                .exportImmobilisationsExcel(perimetreService.serviceIdDe(currentUser));
        return buildResponse(stream,
                "immobilisations_" + LocalDate.now().format(DATE_FORMAT) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/mouvements/excel")
    public ResponseEntity<InputStreamResource> exportMouvementsExcel(
            @AuthenticationPrincipal Utilisateur currentUser) {
        ByteArrayInputStream stream = exportService
                .exportMouvementsExcel(perimetreService.serviceIdDe(currentUser));
        return buildResponse(stream,
                "mouvements_" + LocalDate.now().format(DATE_FORMAT) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /** Demandes : périmètre par identité (admin = toutes, employé = les siennes). */
    @GetMapping("/demandes/excel")
    public ResponseEntity<InputStreamResource> exportDemandesExcel(
            @AuthenticationPrincipal Utilisateur currentUser) {
        ByteArrayInputStream stream = exportService.exportDemandesExcel(currentUser);
        return buildResponse(stream,
                "demandes_" + LocalDate.now().format(DATE_FORMAT) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/inventaires/{campagneId}/excel")
    public ResponseEntity<InputStreamResource> exportInventaireExcel(@PathVariable Long campagneId) {
        ByteArrayInputStream stream = exportService.exportInventaireExcel(campagneId);
        return buildResponse(stream,
                "inventaire_" + campagneId + "_" + LocalDate.now().format(DATE_FORMAT) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // ============ PDF ============

    @GetMapping("/immobilisations/{id}/pdf")
    public ResponseEntity<InputStreamResource> exportFicheImmobilisationPdf(
            @PathVariable Long id,
            @AuthenticationPrincipal Utilisateur currentUser) {
        ByteArrayInputStream stream = exportService.exportFicheImmobilisationPdf(id, currentUser);
        return buildResponse(stream,
                "fiche_immobilisation_" + id + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    @GetMapping("/inventaires/{campagneId}/pdf")
    public ResponseEntity<InputStreamResource> exportInventairePdf(@PathVariable Long campagneId) {
        ByteArrayInputStream stream = exportService.exportInventairePdf(campagneId);
        return buildResponse(stream,
                "inventaire_" + campagneId + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    // ============ HELPERS ============

    private ResponseEntity<InputStreamResource> buildResponse(ByteArrayInputStream stream, String filename, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(stream));
    }
}