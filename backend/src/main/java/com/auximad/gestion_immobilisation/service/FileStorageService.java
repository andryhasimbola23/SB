package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public String storeUserPhoto(MultipartFile file, Long userId) {
        return storeFile(file, "photos", "user_" + userId);
    }

    public String storeFile(MultipartFile file, String subDir, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }

        // Vérifier le type de fichier
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Seuls les fichiers images sont autorisés");
        }

        // Vérifier la taille (max 5 MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La taille du fichier ne doit pas dépasser 5 MB");
        }

        try {
            // Créer le dossier si nécessaire
            Path dirPath = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(dirPath);

            // Générer un nom de fichier unique
            String extension = getExtension(file.getOriginalFilename());
            String fileName = prefix + "_" + UUID.randomUUID().toString() + extension;

            // Copier le fichier
            Path targetPath = dirPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Retourner l'URL relative
            return "/uploads/" + subDir + "/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'enregistrement du fichier : " + e.getMessage(), e);
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) return;
        try {
            String relativePath = fileUrl.replace("/uploads/", "");
            Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log silencieux : si le fichier n'existe pas, ce n'est pas grave
            System.err.println("Erreur lors de la suppression du fichier : " + e.getMessage());
        }
    }
    public String storeImmobilisationPhoto(MultipartFile file) {
        return storeFile(file, "immobilisations", "immo");
    }

    private String getExtension(String fileName) {
        if (fileName == null) return ".jpg";
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0) ? fileName.substring(dotIndex) : ".jpg";
    }
}