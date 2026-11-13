package com.auximad.gestion_immobilisation.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Génère un QR Code avec le logo AUXIMAD au centre.
     * @param contenu Le texte à encoder (ex: "90/26/SAG/2183/UCB/003")
     * @param nomFichier Nom du fichier sans extension
     * @return Le chemin relatif de l'image générée
     */
    public String genererQrCodeAvecLogo(String contenu, String nomFichier) {
        try {
            int taille = 400;
            String format = "PNG";

            // Configuration du QR Code
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 1);

            // Générer la matrice QR
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(contenu, BarcodeFormat.QR_CODE, taille, taille, hints);

            // Convertir en BufferedImage
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Charger le logo AUXIMAD
            BufferedImage logoImage = chargerLogo(taille);

            if (logoImage != null) {
                // Incruster le logo au centre
                int logoSize = taille / 4; // Le logo occupe 1/4 de la taille
                BufferedImage logoResized = redimensionnerImage(logoImage, logoSize, logoSize);

                Graphics2D graphics = qrImage.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Position du logo (centré)
                int x = (taille - logoSize) / 2;
                int y = (taille - logoSize) / 2;

                // Fond blanc arrondi autour du logo (pour meilleure lisibilité)
                int padding = 6;
                graphics.setColor(Color.WHITE);
                graphics.fill(new RoundRectangle2D.Float(
                        x - padding, y - padding,
                        logoSize + 2 * padding, logoSize + 2 * padding,
                        16, 16
                ));

                // Dessiner le logo
                graphics.drawImage(logoResized, x, y, null);
                graphics.dispose();
            }

            // Sauvegarder l'image
            Path qrDirPath = Paths.get(uploadDir, "qrcodes").toAbsolutePath().normalize();
            Files.createDirectories(qrDirPath);

            String fileName = nomFichier + ".png";
            File qrFile = new File(qrDirPath.toFile(), fileName);

            ImageIO.write(qrImage, format, qrFile);

            return "/uploads/qrcodes/" + fileName;
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Erreur lors de la génération du QR Code : " + e.getMessage(), e);
        }
    }

    private BufferedImage chargerLogo(int taille) {
        try {
            // ⚠️ Nom de fichier exact : « logo_auximad.png » (souligné) —
            // l'ancien chemin « logo-auximad.png » (tiret) ne correspondait à
            // aucun fichier : les QR étaient générés SANS logo, en silence.
            java.io.InputStream logoStream = getClass().getClassLoader()
                    .getResourceAsStream("static/logo_auximad.png");

            if (logoStream == null) {
                // Essayer depuis la racine du classpath
                logoStream = getClass().getClassLoader()
                        .getResourceAsStream("logo_auximad.png");
            }

            if (logoStream != null) {
                return ImageIO.read(logoStream);
            }
            // Pas de logo : le QR reste scannable (correction d'erreur H),
            // seul l'habillage graphique est absent.
            return null;
        } catch (IOException e) {
            // Ne jamais faire échouer la génération du QR pour le logo.
            return null;
        }
    }

    private BufferedImage redimensionnerImage(BufferedImage original, int largeur, int hauteur) {
        BufferedImage resized = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, largeur, hauteur, null);
        g.dispose();
        return resized;
    }
}