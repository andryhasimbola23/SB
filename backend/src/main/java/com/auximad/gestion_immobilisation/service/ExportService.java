package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.DemandeDto;
import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.model.Mouvement;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.CampagneInventaireRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.LigneInventaireRepository;
import com.auximad.gestion_immobilisation.repository.MouvementRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ImmobilisationRepository immobilisationRepository;
    private final MouvementRepository mouvementRepository;
    private final CampagneInventaireRepository campagneRepository;
    private final LigneInventaireRepository ligneRepository;
    private final AmortissementService amortissementService;
    private final PerimetreService perimetreService;
    private final DemandeMetier demandeMetier;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Répertoire des fichiers uploadés (photos, QR codes) — même clé que {@code FileStorageService}. */
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /** Marges de la fiche PDF, en points (1 pt ≈ 0,35 mm) — fiche sur une seule page. */
    private static final float MARGE_HAUTE = 24f;
    private static final float MARGE_COTE = 40f;
    private static final float MARGE_BASSE = 38f;

    /** Longueurs maximales des textes libres affichés — garantit la page unique. */
    private static final int MAX_DESIGNATION_PDF = 70;
    private static final int MAX_DESCRIPTION_PDF = 280;

    /** Nombre de mouvements affichés dans l'historique de la fiche. */
    private static final int NB_MOUVEMENTS_FICHE = 5;

    /** Fond des bandes alternées des tableaux de la fiche. */
    private static final com.itextpdf.kernel.colors.Color BANDE_CLAIRE = new DeviceRgb(248, 250, 252);

    // ========================================================================
    // EXPORT EXCEL - LISTE DES IMMOBILISATIONS
    // ========================================================================

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportImmobilisationsExcel(Long serviceId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Immobilisations");

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            // Titre
            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LISTE DES IMMOBILISATIONS - AUXIMAD");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            // Sous-titre
            Row subtitleRow = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell subtitleCell = subtitleRow.createCell(0);
            subtitleCell.setCellValue("Exporté le " + LocalDateTime.now().format(DATETIME_FORMAT));
            CellStyle subtitleStyle = workbook.createCellStyle();
            Font subtitleFont = workbook.createFont();
            subtitleFont.setItalic(true);
            subtitleFont.setFontHeightInPoints((short) 10);
            subtitleStyle.setFont(subtitleFont);
            subtitleCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));

            // En-têtes
            String[] headers = {
                "Code unique", "Désignation", "Type", "Marque",
                "Catégorie", "Agence", "Service", "Fournisseur",
                "Date acquisition", "Valeur d'achat (Ar)", "Taux (%)", "Statut"
            };

            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données — limitées au périmètre de l'utilisateur
            List<Immobilisation> immobilisations = immobilisationRepository.findAllDansPerimetre(serviceId);
            int rowNum = 4;
            BigDecimal totalValeur = BigDecimal.ZERO;

            for (Immobilisation immo : immobilisations) {
                Row row = sheet.createRow(rowNum++);

                setExcelCellValue(row, 0, immo.getCodeUnique(), dataStyle);
                setExcelCellValue(row, 1, immo.getDesignation(), dataStyle);
                setExcelCellValue(row, 2, immo.getTypeMateriel(), dataStyle);
                setExcelCellValue(row, 3, immo.getMarque(), dataStyle);
                setExcelCellValue(row, 4, immo.getCategorie() != null ? immo.getCategorie().getNomCategorie() : "", dataStyle);
                setExcelCellValue(row, 5, immo.getAgence() != null ? immo.getAgence().getNomAgence() : "", dataStyle);
                setExcelCellValue(row, 6, immo.getService() != null ? immo.getService().getNomService() : "", dataStyle);
                setExcelCellValue(row, 7, immo.getFournisseur() != null ? immo.getFournisseur().getNomFournisseur() : "", dataStyle);
                setExcelCellValue(row, 8, immo.getDateAcquisition() != null ? immo.getDateAcquisition().format(DATE_FORMAT) : "", dateStyle);
                setExcelCellValue(row, 9, immo.getValeurAchat(), montantStyle);
                setExcelCellValue(row, 10, immo.getTauxAmortissement() != null ? immo.getTauxAmortissement().doubleValue() : 0, montantStyle);
                setExcelCellValue(row, 11, translateStatut(immo.getStatut()), dataStyle);

                if (immo.getValeurAchat() != null) {
                    totalValeur = totalValeur.add(immo.getValeurAchat());
                }
            }

            // Ligne total
            Row totalRow = sheet.createRow(rowNum);
            org.apache.poi.ss.usermodel.Cell totalLabel = totalRow.createCell(8);
            totalLabel.setCellValue("TOTAL");
            totalLabel.setCellStyle(headerStyle);

            org.apache.poi.ss.usermodel.Cell totalValue = totalRow.createCell(9);
            totalValue.setCellValue(totalValeur.doubleValue());
            totalValue.setCellStyle(montantStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 10000));
            }

            sheet.createFreezePane(0, 4);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    // ========================================================================
    // EXPORT EXCEL - MOUVEMENTS
    // ========================================================================

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportMouvementsExcel(Long serviceId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Mouvements");

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("HISTORIQUE DES MOUVEMENTS - AUXIMAD");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            String[] headers = {
                "Date", "Type", "Code immobilisation", "Désignation",
                "Ancienne localisation", "Nouvelle localisation",
                "VNC (Ar)", "Prix vente (Ar)", "Plus/moins-value (Ar)"
            };

            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var mouvements = mouvementRepository.findAllByOrderByDateMouvementDesc(serviceId);
            int rowNum = 3;

            for (var mvt : mouvements) {
                Row row = sheet.createRow(rowNum++);

                setExcelCellValue(row, 0, mvt.getDateMouvement() != null ? mvt.getDateMouvement().format(DATETIME_FORMAT) : "", dataStyle);
                setExcelCellValue(row, 1, translateTypeMouvement(mvt.getTypeMouvement()), dataStyle);
                setExcelCellValue(row, 2, mvt.getImmobilisation() != null ? mvt.getImmobilisation().getCodeUnique() : "", dataStyle);
                setExcelCellValue(row, 3, mvt.getImmobilisation() != null ? mvt.getImmobilisation().getDesignation() : "", dataStyle);
                setExcelCellValue(row, 4, mvt.getAncienneLocalisation() != null ? mvt.getAncienneLocalisation() : "", dataStyle);
                setExcelCellValue(row, 5, mvt.getNouvelleLocalisation() != null ? mvt.getNouvelleLocalisation() : "", dataStyle);
                setExcelCellValue(row, 6, mvt.getValeurVnc(), montantStyle);
                setExcelCellValue(row, 7, mvt.getPrixVente(), montantStyle);
                setExcelCellValue(row, 8, mvt.getPlusMoinsValue(), montantStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 10000));
            }

            sheet.createFreezePane(0, 3);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    // ========================================================================
    // EXPORT EXCEL - INVENTAIRE
    // ========================================================================

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportInventaireExcel(Long campagneId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            var campagne = campagneRepository.findById(campagneId)
                    .orElseThrow(() -> new RuntimeException("Campagne non trouvée"));

            XSSFSheet sheet = workbook.createSheet("Inventaire");

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("RAPPORT D'INVENTAIRE - " + campagne.getLibelle().toUpperCase());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            Row infoRow = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell infoCell = infoRow.createCell(0);
            infoCell.setCellValue(
                "Période : " + campagne.getDateDebut().format(DATE_FORMAT) +
                (campagne.getDateFin() != null ? " au " + campagne.getDateFin().format(DATE_FORMAT) : "") +
                " | Agence : " + (campagne.getAgence() != null ? campagne.getAgence().getNomAgence() : "Toutes")
            );
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            String[] headers = {"Code", "Désignation", "État constaté", "Localisation", "Date scan", "Observation"};

            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var lignes = ligneRepository.findByCampagneIdOrderByDateScanDesc(campagneId);
            int rowNum = 4;

            for (var ligne : lignes) {
                Row row = sheet.createRow(rowNum++);

                setExcelCellValue(row, 0, ligne.getImmobilisation().getCodeUnique(), dataStyle);
                setExcelCellValue(row, 1, ligne.getImmobilisation().getDesignation(), dataStyle);
                setExcelCellValue(row, 2, translateEtatInventaire(ligne.getEtatConstate()), dataStyle);
                setExcelCellValue(row, 3, ligne.getLocalisationConstatee() != null ? ligne.getLocalisationConstatee() : "", dataStyle);
                setExcelCellValue(row, 4, ligne.getDateScan() != null ? ligne.getDateScan().format(DATETIME_FORMAT) : "", dataStyle);
                setExcelCellValue(row, 5, ligne.getObservation() != null ? ligne.getObservation() : "", dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 10000));
            }

            sheet.createFreezePane(0, 4);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    // ========================================================================
    // EXPORT EXCEL - DEMANDES D'IMMOBILISATION
    // ========================================================================

    /**
     * Export des demandes, filtré par le périmètre de l'appelant via
     * {@link DemandeMetier} : un ADMIN obtient toutes les demandes, un EMPLOYE
     * uniquement les siennes. La règle de périmètre n'est jamais réécrite ici.
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportDemandesExcel(Utilisateur user) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Demandes");

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("DEMANDES D'IMMOBILISATION - AUXIMAD");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

            Row dateRow = sheet.createRow(1);
            org.apache.poi.ss.usermodel.Cell dateCell = dateRow.createCell(0);
            dateCell.setCellValue("Exporté le " + LocalDateTime.now().format(DATETIME_FORMAT));
            CellStyle dateCellStyle = workbook.createCellStyle();
            Font dateFont = workbook.createFont();
            dateFont.setItalic(true);
            dateFont.setFontHeightInPoints((short) 10);
            dateCellStyle.setFont(dateFont);
            dateCell.setCellStyle(dateCellStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 12));

            String[] headers = {
                "Référence", "Date", "Désignation", "Motif", "Quantité",
                "Priorité", "Besoin le", "Statut", "Demandeur", "Service",
                "Catégorie", "Traité le", "Traité par", "Commentaire"
            };

            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<DemandeDto> demandes = demandeMetier.getAll(user, null, null);
            int rowNum = 4;

            for (DemandeDto d : demandes) {
                Row row = sheet.createRow(rowNum++);

                setExcelCellValue(row, 0, nvl(d.getReference()), dataStyle);
                setExcelCellValue(row, 1, d.getDateCreation() != null ? d.getDateCreation().format(DATETIME_FORMAT) : "", dataStyle);
                setExcelCellValue(row, 2, nvl(d.getDesignation()), dataStyle);
                setExcelCellValue(row, 3, nvl(d.getMotif()), dataStyle);
                setExcelCellValue(row, 4, d.getQuantite() != null ? d.getQuantite().doubleValue() : 0, dataStyle);
                setExcelCellValue(row, 5, translatePrioriteDemande(d.getPriorite()), dataStyle);
                setExcelCellValue(row, 6, d.getBesoinLe() != null ? d.getBesoinLe().format(DATE_FORMAT) : "", dataStyle);
                setExcelCellValue(row, 7, translateStatutDemande(d.getStatut()), dataStyle);
                setExcelCellValue(row, 8, nvl(d.getDemandeurNom()), dataStyle);
                setExcelCellValue(row, 9, nvl(d.getServiceAffiche()), dataStyle);
                setExcelCellValue(row, 10, nvl(d.getCategorieNom()), dataStyle);
                setExcelCellValue(row, 11, d.getDateTraitement() != null ? d.getDateTraitement().format(DATETIME_FORMAT) : "", dataStyle);
                setExcelCellValue(row, 12, nvl(d.getTraiteeParNom()), dataStyle);
                setExcelCellValue(row, 13, nvl(d.getCommentaireTraitement()), dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 10000));
            }

            sheet.createFreezePane(0, 4);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    // ========================================================================
    // EXPORT PDF - FICHE IMMOBILISATION
    // ========================================================================

    /**
     * Fiche PDF d'une immobilisation.
     *
     * <p>Vérifie le périmètre avant tout — sinon un employé téléchargerait la
     * fiche de n'importe quel bien de l'entreprise. La fiche comprend le logo
     * AUXIMAD, un bandeau d'identité (code unique, pastille de statut, QR
     * code), la photo du bien, l'historique des mouvements, l'auteur de
     * l'édition et un pied de page paginé « Page x / y ».</p>
     *
     * <p>Le document est construit en deux passes : la première (sur un jeton
     * jetable) dénombre les pages, la seconde produit le fichier avec une
     * pagination exacte — iText ne connaît le total des pages qu'une fois le
     * document terminé.</p>
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportFicheImmobilisationPdf(Long immobilisationId, Utilisateur user) {
        // Contrôle du périmètre EN DEHORS du try : le catch ci-dessous absorberait
        // sinon l'exception et la transformerait en 500, ce qui masquerait le refus
        // (et donnerait « erreur serveur » au lieu d'un 404).
        Immobilisation immo = perimetreService.exigerAccessible(
                immobilisationRepository.findById(immobilisationId), user);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Passe 1 (jeton jetable) : dénombre les pages pour la pagination.
            int totalPages;
            try (ByteArrayOutputStream jeton = new ByteArrayOutputStream()) {
                PdfDocument docJeton = new PdfDocument(new PdfWriter(jeton));
                Document passe = new Document(docJeton);
                passe.setMargins(MARGE_HAUTE, MARGE_COTE, MARGE_BASSE, MARGE_COTE);
                remplirFiche(passe, immo, user);
                totalPages = docJeton.getNumberOfPages();
                passe.close();
            }

            // Passe 2 : document final avec métadonnées et pied de page paginé.
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            Document document = new Document(pdf);
            document.setMargins(MARGE_HAUTE, MARGE_COTE, MARGE_BASSE, MARGE_COTE);

            pdf.getDocumentInfo()
                    .setTitle("Fiche immobilisation " + immo.getCodeUnique())
                    .setAuthor("AUXIMAD")
                    .setSubject("Fiche d'immobilisation — " + immo.getDesignation())
                    .setCreator("Système de Gestion des Immobilisations AUXIMAD");
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE,
                    piedDePage(immo.getCodeUnique(), totalPages));

            remplirFiche(document, immo, user);

            document.close();
            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    /**
     * Construit l'intégralité du contenu de la fiche (sans pied de page) :
     * en-tête, bandeau d'identité, identification, description, dates,
     * valeurs, affectation, historique des mouvements et éditeur.
     */
    private void remplirFiche(Document document, Immobilisation immo, Utilisateur user) {
        com.itextpdf.kernel.colors.Color greenColor = new DeviceRgb(0, 150, 64);
        com.itextpdf.kernel.colors.Color redColor = new DeviceRgb(227, 6, 19);
        com.itextpdf.kernel.colors.Color darkColor = new DeviceRgb(30, 41, 59);
        com.itextpdf.kernel.colors.Color filetClair = new DeviceRgb(226, 232, 240);

        // ------------------------------------------------------------------
        // EN-TÊTE : logo + marque | titre | date d'édition et référence
        // ------------------------------------------------------------------
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{34, 42, 24}))
                .useAllAvailableWidth();

        com.itextpdf.layout.element.Cell marqueCell = new com.itextpdf.layout.element.Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(0);
        Image logo = chargerImageClasspath("static/logo_auximad.png");
        if (logo != null) {
            marqueCell.add(new Paragraph()
                    .add(logo.scaleToFit(38, 38))
                    .setMarginBottom(2));
        }
        marqueCell.add(new Paragraph("AUXIMAD").setFontSize(17).setBold().setFontColor(redColor));
        marqueCell.add(new Paragraph("Gestion des Immobilisations")
                .setFontSize(8).setFontColor(ColorConstants.GRAY));
        headerTable.addCell(marqueCell);

        headerTable.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("FICHE D'IMMOBILISATION")
                        .setFontSize(14).setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(darkColor)
                        .setMarginTop(4))
                .add(new Paragraph("Détail du bien, valeurs et historique")
                        .setFontSize(8)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.GRAY))
                .setBorder(Border.NO_BORDER));

        headerTable.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("Éditée le " + LocalDateTime.now().format(DATETIME_FORMAT))
                        .setFontSize(9).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Réf. " + immo.getCodeUnique())
                        .setFontSize(9).setBold()
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setFontColor(greenColor))
                .setBorder(Border.NO_BORDER));

        document.add(headerTable);

        // Filet bicolore aux couleurs de la marque
        document.add(new Paragraph(" ").setFontSize(1).setMarginTop(4).setMarginBottom(0)
                .setBorderBottom(new SolidBorder(redColor, 2.5f)));
        document.add(new Paragraph(" ").setFontSize(1).setMarginBottom(10)
                .setBorderBottom(new SolidBorder(greenColor, 1.5f)));

        // ------------------------------------------------------------------
        // BANDEAU D'IDENTITÉ : code unique + pastille de statut + QR code
        // ------------------------------------------------------------------
        Table bandeau = new Table(UnitValue.createPercentArray(new float[]{52, 20, 28}))
                .useAllAvailableWidth()
                .setMarginBottom(6);

        bandeau.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("CODE UNIQUE").setFontSize(8).setBold()
                        .setFontColor(ColorConstants.GRAY).setMarginBottom(2))
                .add(new Paragraph(immo.getCodeUnique()).setFontSize(16).setBold()
                        .setFontColor(greenColor))
                .setBackgroundColor(BANDE_CLAIRE)
                .setBorder(new SolidBorder(filetClair, 1))
                .setPadding(8)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE));

        bandeau.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("STATUT").setFontSize(7).setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.WHITE).setMarginBottom(1))
                .add(new Paragraph(translateStatut(immo.getStatut()))
                        .setFontSize(11).setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(couleurStatut(immo.getStatut()))
                .setBorder(Border.NO_BORDER)
                .setPadding(6)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE));

        com.itextpdf.layout.element.Cell qrCell = new com.itextpdf.layout.element.Cell()
                .setBorder(new SolidBorder(filetClair, 1))
                .setPadding(5)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
        Image qr = chargerImageUpload(immo.getQrCode());
        if (qr != null) {
            qrCell.add(new Paragraph()
                    .add(qr.scaleToFit(48, 48))
                    .setTextAlignment(TextAlignment.CENTER));
        } else {
            qrCell.add(new Paragraph("QR code non généré").setFontSize(8)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        bandeau.addCell(qrCell);
        document.add(bandeau);

        // ------------------------------------------------------------------
        // CORPS DE LA FICHE EN DEUX COLONNES — pour tenir sur une seule page
        // ------------------------------------------------------------------
        Table corps = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .useAllAvailableWidth()
                .setMarginBottom(4);
        com.itextpdf.layout.element.Cell colonneGauche = new com.itextpdf.layout.element.Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(0)
                .setPaddingRight(8);
        com.itextpdf.layout.element.Cell colonneDroite = new com.itextpdf.layout.element.Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(0)
                .setPaddingLeft(8);

        // ------------------------------------------------------------------
        // COLONNE GAUCHE : Identification, Description, Dates, Valeurs
        // ------------------------------------------------------------------
        colonneGauche.add(createSectionTitle("Identification", greenColor));
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{34, 66}))
                .useAllAvailableWidth();
        addPdfRow(infoTable, "Désignation", tronquer(immo.getDesignation(), MAX_DESIGNATION_PDF));
        addPdfRow(infoTable, "Type de matériel", immo.getTypeMateriel());
        addPdfRow(infoTable, "Marque", immo.getMarque());
        addPdfRow(infoTable, "État physique", translateEtatPhysique(immo.getEtatPhysique()));
        addPdfRow(infoTable, "Statut", translateStatut(immo.getStatut()));

        Image photo = chargerImageUpload(immo.getPhoto());
        if (photo != null) {
            Table identification = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
                    .useAllAvailableWidth();
            identification.addCell(new com.itextpdf.layout.element.Cell()
                    .add(infoTable)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(0)
                    .setPaddingRight(6));
            identification.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph()
                            .add(photo.scaleToFit(90, 90))
                            .setTextAlignment(TextAlignment.CENTER))
                    .add(new Paragraph("Photo du bien").setFontSize(7)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setFontColor(ColorConstants.GRAY))
                    .setBorder(new SolidBorder(filetClair, 1))
                    .setPadding(4));
            colonneGauche.add(identification);
        } else {
            colonneGauche.add(infoTable);
        }

        colonneGauche.add(createSectionTitle("Description", greenColor)
                .setMarginTop(8));
        String description = (immo.getDescription() != null && !immo.getDescription().isBlank())
                ? tronquer(immo.getDescription(), MAX_DESCRIPTION_PDF)
                : "Aucune description renseignée.";
        colonneGauche.add(new Paragraph(description).setFontSize(10).setMarginBottom(0));

        colonneGauche.add(createSectionTitle("Dates", greenColor)
                .setMarginTop(8));
        Table datesTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}))
                .useAllAvailableWidth();
        addPdfRow(datesTable, "Date d'acquisition",
                immo.getDateAcquisition() != null ? immo.getDateAcquisition().format(DATE_FORMAT) : "-");
        addPdfRow(datesTable, "Date de mise en service",
                immo.getDateMiseService() != null ? immo.getDateMiseService().format(DATE_FORMAT) : "-");
        colonneGauche.add(datesTable);

        colonneGauche.add(createSectionTitle("Valeurs et amortissement", greenColor)
                .setMarginTop(8));
        Table valeursTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}))
                .useAllAvailableWidth();
        addPdfRow(valeursTable, "Valeur d'achat", formatMontant(immo.getValeurAchat()));
        addPdfRow(valeursTable, "Taux d'amortissement", immo.getTauxAmortissement() + " %");
        try {
            BigDecimal vnc = amortissementService.calculerVncActuelle(immo.getId());
            addPdfRow(valeursTable, "VNC actuelle", formatMontant(vnc));
        } catch (Exception e) {
            addPdfRow(valeursTable, "VNC actuelle", "Non calculable");
        }
        colonneGauche.add(valeursTable);

        // ------------------------------------------------------------------
        // COLONNE DROITE : Affectation, Historique, Édité par
        // ------------------------------------------------------------------
        colonneDroite.add(createSectionTitle("Affectation", greenColor));
        Table affectationTable = new Table(UnitValue.createPercentArray(new float[]{38, 62}))
                .useAllAvailableWidth();
        addPdfRow(affectationTable, "Agence",
                immo.getAgence() != null ? immo.getAgence().getNomAgence() : "-");
        addPdfRow(affectationTable, "Service",
                immo.getService() != null ? immo.getService().getNomService() : "-");
        addPdfRow(affectationTable, "Catégorie",
                immo.getCategorie() != null ? immo.getCategorie().getNomCategorie() : "-");
        addPdfRow(affectationTable, "Fournisseur",
                immo.getFournisseur() != null ? immo.getFournisseur().getNomFournisseur() : "-");
        colonneDroite.add(affectationTable);

        colonneDroite.add(createSectionTitle("Historique des mouvements", greenColor)
                .setMarginTop(8));
        List<Mouvement> mouvements = mouvementRepository
                .findByImmobilisationIdOrderByDateMouvementDesc(immo.getId(), null);
        if (mouvements.isEmpty()) {
            colonneDroite.add(new Paragraph("Aucun mouvement enregistré pour ce bien.")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY));
        } else {
            Table historique = new Table(UnitValue.createPercentArray(new float[]{24, 20, 28, 28}))
                    .useAllAvailableWidth();
            addPdfHeaderCell(historique, "Date", greenColor);
            addPdfHeaderCell(historique, "Type", greenColor);
            addPdfHeaderCell(historique, "Départ", greenColor);
            addPdfHeaderCell(historique, "Arrivée", greenColor);

            List<Mouvement> derniers = mouvements.stream().limit(NB_MOUVEMENTS_FICHE).toList();
            for (int i = 0; i < derniers.size(); i++) {
                Mouvement mvt = derniers.get(i);
                com.itextpdf.kernel.colors.Color bande = i % 2 == 0 ? BANDE_CLAIRE : ColorConstants.WHITE;
                historique.addCell(ligneHistorique(
                        mvt.getDateMouvement() != null ? mvt.getDateMouvement().format(DATETIME_FORMAT) : "-",
                        bande));
                historique.addCell(ligneHistorique(translateTypeMouvement(mvt.getTypeMouvement()), bande));
                historique.addCell(ligneHistorique(texteOuTiret(mvt.getAncienneLocalisation()), bande));
                historique.addCell(ligneHistorique(texteOuTiret(mvt.getNouvelleLocalisation()), bande));
            }
            colonneDroite.add(historique);

            if (mouvements.size() > NB_MOUVEMENTS_FICHE) {
                colonneDroite.add(new Paragraph("… et " + (mouvements.size() - NB_MOUVEMENTS_FICHE)
                                + " autre(s) mouvement(s) — détail dans l'application.")
                        .setFontSize(8).setFontColor(ColorConstants.GRAY).setMarginTop(4));
            }
        }

        colonneDroite.add(createSectionTitle("Édité par", greenColor)
                .setMarginTop(8));
        Table editeur = new Table(UnitValue.createPercentArray(new float[]{38, 62}))
                .useAllAvailableWidth();
        String nomEditeur = ((user.getPrenom() != null ? user.getPrenom() : "") + " "
                + (user.getNom() != null ? user.getNom() : "")).trim();
        addPdfRow(editeur, "Utilisateur",
                (nomEditeur.isEmpty() ? user.getNomUtilisateur() : nomEditeur)
                        + " (" + user.getNomUtilisateur() + ")");
        addPdfRow(editeur, "Rôle", user.getRole() != null ? translateRole(user.getRole()) : "-");
        addPdfRow(editeur, "Date d'édition", LocalDateTime.now().format(DATETIME_FORMAT));
        colonneDroite.add(editeur);

        corps.addCell(colonneGauche);
        corps.addCell(colonneDroite);
        document.add(corps);

        // ------------------------------------------------------------------
        // PIED DE PAGE DU DOCUMENT (mention — la pagination est au trait)
        // ------------------------------------------------------------------
        document.add(new Paragraph().setBorderTop(new SolidBorder(greenColor, 1))
                .setFontSize(1).setMarginBottom(1));
        document.add(new Paragraph("Document généré automatiquement par le Système de Gestion des Immobilisations AUXIMAD")
                .setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER));
    }

    // ========================================================================
    // EXPORT PDF - RAPPORT D'INVENTAIRE
    // ========================================================================

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportInventairePdf(Long campagneId) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            var campagne = campagneRepository.findById(campagneId)
                    .orElseThrow(() -> new RuntimeException("Campagne non trouvée"));

            var lignes = ligneRepository.findByCampagneIdOrderByDateScanDesc(campagneId);

            com.itextpdf.kernel.colors.Color greenColor = new DeviceRgb(0, 150, 64);
            com.itextpdf.kernel.colors.Color redColor = new DeviceRgb(227, 6, 19);

            // EN-TÊTE
            document.add(new Paragraph("AUXIMAD").setFontSize(20).setBold().setFontColor(redColor));
            document.add(new Paragraph("RAPPORT D'INVENTAIRE")
                    .setFontSize(16).setBold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph().setBorderBottom(new SolidBorder(greenColor, 3)).setMarginBottom(15));

            // INFOS CAMPAGNE
            document.add(new Paragraph("Campagne : " + campagne.getLibelle()).setFontSize(12).setBold());
            document.add(new Paragraph("Période : " +
                    campagne.getDateDebut().format(DATE_FORMAT) +
                    (campagne.getDateFin() != null ? " au " + campagne.getDateFin().format(DATE_FORMAT) : ""))
                    .setFontSize(10));
            document.add(new Paragraph("Agence : " +
                    (campagne.getAgence() != null ? campagne.getAgence().getNomAgence() : "Toutes"))
                    .setFontSize(10));
            document.add(new Paragraph("Statut : " + translateStatut(campagne.getStatut())).setFontSize(10));
            document.add(new Paragraph("\n"));

            // STATISTIQUES
            long total = immobilisationRepository.count();
            long presentes = lignes.stream().filter(l -> "PRESENT".equals(l.getEtatConstate())).count();
            long manquants = lignes.stream().filter(l -> "MANQUANT".equals(l.getEtatConstate())).count();
            long mauvaisEtat = lignes.stream().filter(l -> "MAUVAIS_ETAT".equals(l.getEtatConstate())).count();

            document.add(createSectionTitle("Statistiques", greenColor));

            Table statsTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            addPdfRow(statsTable, "Total biens dans le parc", String.valueOf(total));
            addPdfRow(statsTable, "Biens scannés", String.valueOf(lignes.size()));
            addPdfRow(statsTable, "Présents", String.valueOf(presentes));
            addPdfRow(statsTable, "Manquants", String.valueOf(manquants));
            addPdfRow(statsTable, "Mauvais état", String.valueOf(mauvaisEtat));

            document.add(statsTable);
            document.add(new Paragraph("\n"));

            // LISTE
            document.add(createSectionTitle("Détail des biens scannés", greenColor));

            Table table = new Table(UnitValue.createPercentArray(new float[]{20, 35, 20, 25}))
                    .useAllAvailableWidth();

            addPdfHeaderCell(table, "Code", greenColor);
            addPdfHeaderCell(table, "Désignation", greenColor);
            addPdfHeaderCell(table, "État", greenColor);
            addPdfHeaderCell(table, "Date scan", greenColor);

            for (var ligne : lignes) {
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(ligne.getImmobilisation().getCodeUnique()).setFontSize(8)));
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(ligne.getImmobilisation().getDesignation()).setFontSize(8)));
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(translateEtatInventaire(ligne.getEtatConstate())).setFontSize(8)));
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(ligne.getDateScan() != null ? ligne.getDateScan().format(DATETIME_FORMAT) : "-").setFontSize(8)));
            }

            document.add(table);

            // PIED
            document.add(new Paragraph("\n"));
            document.add(new Paragraph().setBorderTop(new SolidBorder(greenColor, 1)));
            document.add(new Paragraph("Rapport généré le " + LocalDateTime.now().format(DATETIME_FORMAT) +
                    " par le Système de Gestion des Immobilisations AUXIMAD")
                    .setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER));

            document.close();
            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    // ========================================================================
    // HELPERS EXCEL
    // ========================================================================

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        return style;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createDataStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createMontantStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CellStyle createDateStyle(XSSFWorkbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void setExcelCellValue(Row row, int col, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void setExcelCellValue(Row row, int col, Double value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setExcelCellValue(Row row, int col, BigDecimal value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    // ========================================================================
    // HELPERS PDF
    // ========================================================================

    private Paragraph createSectionTitle(String title, com.itextpdf.kernel.colors.Color color) {
        return new Paragraph(title)
                .setFontSize(12)
                .setBold()
                .setFontColor(color)
                .setMarginTop(5)
                .setMarginBottom(8)
                .setBorderBottom(new SolidBorder(color, 1));
    }

    private void addPdfRow(Table table, String label, String value) {
        // Bandes alternées : la ligne paire prend un fond clair (lecture facilitée).
        com.itextpdf.kernel.colors.Color bande = table.getNumberOfRows() % 2 == 0
                ? BANDE_CLAIRE : ColorConstants.WHITE;
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(label).setBold().setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(bande)
                .setPadding(4));
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(value != null ? value : "-").setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(bande)
                .setPadding(4));
    }

    private void addPdfHeaderCell(Table table, String text, com.itextpdf.kernel.colors.Color color) {
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                .setBackgroundColor(color)
                .setPadding(5));
    }

    /** Cellule de l'historique des mouvements (fond de bande alternée). */
    private com.itextpdf.layout.element.Cell ligneHistorique(
            String texte, com.itextpdf.kernel.colors.Color bande) {
        return new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(texte).setFontSize(9))
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(bande)
                .setPadding(4);
    }

    private String texteOuTiret(String valeur) {
        return (valeur == null || valeur.isBlank()) ? "-" : valeur;
    }

    /**
     * Tronque un texte libre (désignation, description) pour garantir que la
     * fiche reste sur une seule page. Le texte complet reste consultable dans
     * l'application.
     */
    private String tronquer(String texte, int max) {
        if (texte == null || texte.length() <= max) {
            return texte;
        }
        return texte.substring(0, max).trim() + "…";
    }

    /**
     * Charge une image des ressources embarquées (logo de la marque).
     * Retourne {@code null} si le fichier est absent : l'export ne casse jamais.
     */
    private Image chargerImageClasspath(String chemin) {
        try {
            byte[] donnees = new ClassPathResource(chemin).getContentAsByteArray();
            return new Image(ImageDataFactory.create(donnees));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Charge une image uploadée (photo du bien, QR code) depuis le disque.
     * Les URLs stockées en base sont relatives ({@code /uploads/...}) et
     * pointent sous {@code file.upload-dir}. Retourne {@code null} si le
     * fichier n'existe pas ; le chemin reste confiné au répertoire d'upload.
     */
    private Image chargerImageUpload(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String relatif = url.startsWith("/uploads/")
                    ? url.substring("/uploads/".length())
                    : url.replaceFirst("^/+", "");
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path fichier = base.resolve(relatif).normalize();
            if (!fichier.startsWith(base) || !Files.isRegularFile(fichier)) {
                return null;
            }
            return new Image(ImageDataFactory.create(fichier.toString()));
        } catch (Exception e) {
            return null;
        }
    }

    /** Couleur de la pastille de statut aux couleurs de l'application. */
    private com.itextpdf.kernel.colors.Color couleurStatut(String statut) {
        if (statut == null) return new DeviceRgb(100, 116, 139);
        return switch (statut) {
            case "ACTIF" -> new DeviceRgb(0, 150, 64);
            case "CEDE" -> new DeviceRgb(245, 158, 11);
            case "REBUTE" -> new DeviceRgb(227, 6, 19);
            default -> new DeviceRgb(100, 116, 139);
        };
    }

    private String translateRole(com.auximad.gestion_immobilisation.model.Role role) {
        return switch (role) {
            case ADMIN -> "Administrateur";
            case EMPLOYE -> "Employé";
        };
    }

    /**
     * Pied de page de chaque page : référence à gauche, pagination exacte
     * « Page x / y » au centre, date d'édition à droite.
     *
     * <p>Un échec ici est purement décoratif : il ne doit jamais faire
     * échouer la génération du document.</p>
     */
    private IEventHandler piedDePage(String codeUnique, int totalPages) {
        String dateEdition = LocalDateTime.now().format(DATETIME_FORMAT);
        return evenement -> {
            try {
                PdfDocumentEvent evt = (PdfDocumentEvent) evenement;
                var page = evt.getPage();
                var cadre = page.getPageSize();
                float y = cadre.getBottom() + 20;
                float xGauche = cadre.getLeft() + MARGE_COTE;
                float xDroite = cadre.getRight() - MARGE_COTE;

                String gauche = "AUXIMAD · Fiche " + codeUnique;
                String centre = "Page " + evt.getDocument().getPageNumber(page) + " / " + totalPages;
                String droite = "Éditée le " + dateEdition;

                PdfFont fonte = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                PdfCanvas canvas = new PdfCanvas(
                        page.newContentStreamAfter(), page.getResources(), evt.getDocument());
                canvas.saveState();
                canvas.setFillColor(new DeviceRgb(100, 116, 139));

                canvas.beginText().setFontAndSize(fonte, 8)
                        .moveText(xGauche, y).showText(gauche).endText();

                float largeurCentre = fonte.getWidth(centre, 8);
                canvas.beginText().setFontAndSize(fonte, 8)
                        .moveText((cadre.getLeft() + cadre.getRight()) / 2f - largeurCentre / 2f, y)
                        .showText(centre).endText();

                float largeurDroite = fonte.getWidth(droite, 8);
                canvas.beginText().setFontAndSize(fonte, 8)
                        .moveText(xDroite - largeurDroite, y).showText(droite).endText();

                canvas.restoreState();
                canvas.release();
            } catch (Exception ignore) {
                // Décoratif : jamais bloquant pour l'export.
            }
        };
    }

    // ========================================================================
    // HELPERS TRADUCTION
    // ========================================================================

    private String translateStatut(String statut) {
        if (statut == null) return "-";
        return switch (statut) {
            case "ACTIF" -> "Actif";
            case "CEDE" -> "Cédé";
            case "REBUTE" -> "Mis au rebut";
            case "EN_COURS" -> "En cours";
            case "CLOTURE" -> "Clôturée";
            default -> statut;
        };
    }

    private String translateStatutDemande(String statut) {
        if (statut == null) return "-";
        return switch (statut) {
            case "EN_ATTENTE" -> "En attente";
            case "APPROUVEE" -> "Approuvée";
            case "REJETTEE" -> "Rejetée";
            case "ANNULEE" -> "Annulée";
            default -> statut;
        };
    }

    private String translatePrioriteDemande(String priorite) {
        if (priorite == null) return "Normale";
        return switch (priorite) {
            case "URGENTE" -> "Urgente";
            case "NORMALE" -> "Normale";
            default -> priorite;
        };
    }

    /** Chaîne vide au lieu de « null » dans les colonnes Excel. */
    private String nvl(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private String translateEtatPhysique(String etat) {
        if (etat == null) return "-";
        return switch (etat) {
            case "NEUF" -> "Neuf";
            case "BON" -> "Bon état";
            case "MAUVAIS" -> "Mauvais état";
            case "HORS_SERVICE" -> "Hors service";
            default -> etat;
        };
    }

    private String translateEtatInventaire(String etat) {
        if (etat == null) return "-";
        return switch (etat) {
            case "PRESENT" -> "Présent";
            case "MANQUANT" -> "Manquant";
            case "MAUVAIS_ETAT" -> "Mauvais état";
            default -> etat;
        };
    }

    private String translateTypeMouvement(String type) {
        if (type == null) return "-";
        return switch (type) {
            case "ENTREE" -> "Entrée";
            case "TRANSFERT" -> "Transfert";
            case "AFFECTATION" -> "Affectation";
            case "CESSION" -> "Cession";
            case "REBUT" -> "Mise au rebut";
            default -> type;
        };
    }

    private String formatMontant(BigDecimal montant) {
        if (montant == null) return "-";
        return String.format("%,.2f Ar", montant);
    }
}