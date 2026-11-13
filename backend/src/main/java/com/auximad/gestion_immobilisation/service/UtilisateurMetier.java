package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ChangePasswordDto;
import com.auximad.gestion_immobilisation.dto.ProfilDto;
import com.auximad.gestion_immobilisation.dto.UtilisateurDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.CampagneInventaireRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.MouvementRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisateurMetier {

    private final UtilisateurRepository utilisateurRepository;
    private final ServiceRepository serviceRepository;
    private final AgenceRepository agenceRepository;
    private final MouvementRepository mouvementRepository;
    private final CampagneInventaireRepository campagneRepository;
    private final DemandeImmobilisationRepository demandeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JournalAuditMetier journalAuditMetier;

    @Transactional(readOnly = true)
    public List<UtilisateurDto> getAllUtilisateurs() {
        return utilisateurRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UtilisateurDto getUtilisateurById(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID : " + id));
        return toDto(utilisateur);
    }

    @Transactional
    public UtilisateurDto createUtilisateur(UtilisateurDto dto) {
        // Vérifier unicité du nom d'utilisateur
        if (utilisateurRepository.existsByNomUtilisateur(dto.getNomUtilisateur())) {
            throw new IllegalArgumentException("Le nom d'utilisateur '" + dto.getNomUtilisateur() + "' est déjà utilisé.");
        }

        // Vérifier unicité de l'email
        if (utilisateurRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("L'email '" + dto.getEmail() + "' est déjà utilisé.");
        }

        // Vérifier que le mot de passe est fourni
        if (dto.getMotDePasse() == null || dto.getMotDePasse().trim().isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire pour la création.");
        }

        // Construire l'utilisateur
        Utilisateur utilisateur = Utilisateur.builder()
                .nomUtilisateur(dto.getNomUtilisateur().trim())
                .motDePasse(passwordEncoder.encode(dto.getMotDePasse()))
                .email(dto.getEmail().trim())
                .nom(dto.getNom().trim())
                .prenom(dto.getPrenom().trim())
                .telephone(dto.getTelephone())
                .photo(dto.getPhoto())
                .role(Role.valueOf(dto.getRole().toUpperCase()))
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .build();

        // Relations
        if (dto.getServiceId() != null) {
            com.auximad.gestion_immobilisation.model.Service service = serviceRepository.findById(dto.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé avec l'ID : " + dto.getServiceId()));
            utilisateur.setService(service);
        }
        if (dto.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(dto.getAgenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée avec l'ID : " + dto.getAgenceId()));
            utilisateur.setAgence(agence);
        }

        return toDto(utilisateurRepository.save(utilisateur));
    }

    @Transactional
    public UtilisateurDto updateUtilisateur(Long id, UtilisateurDto dto) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID : " + id));

        // Vérifier unicité du nom d'utilisateur (sauf pour lui-même)
        if (!utilisateur.getNomUtilisateur().equalsIgnoreCase(dto.getNomUtilisateur())
                && utilisateurRepository.existsByNomUtilisateur(dto.getNomUtilisateur())) {
            throw new IllegalArgumentException("Le nom d'utilisateur '" + dto.getNomUtilisateur() + "' est déjà utilisé.");
        }

        // Vérifier unicité de l'email (sauf pour lui-même)
        if (!utilisateur.getEmail().equalsIgnoreCase(dto.getEmail())
                && utilisateurRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("L'email '" + dto.getEmail() + "' est déjà utilisé.");
        }

        utilisateur.setNomUtilisateur(dto.getNomUtilisateur().trim());
        utilisateur.setEmail(dto.getEmail().trim());
        utilisateur.setNom(dto.getNom().trim());
        utilisateur.setPrenom(dto.getPrenom().trim());
        utilisateur.setTelephone(dto.getTelephone());
        utilisateur.setPhoto(dto.getPhoto());
        utilisateur.setRole(Role.valueOf(dto.getRole().toUpperCase()));
        utilisateur.setActif(dto.getActif());

        // Mise à jour du mot de passe SEULEMENT si fourni
        if (dto.getMotDePasse() != null && !dto.getMotDePasse().trim().isEmpty()) {
            utilisateur.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        }

        // Relations
        if (dto.getServiceId() != null) {
            com.auximad.gestion_immobilisation.model.Service service = serviceRepository.findById(dto.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé"));
            utilisateur.setService(service);
        } else {
            utilisateur.setService(null);
        }
        if (dto.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(dto.getAgenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée"));
            utilisateur.setAgence(agence);
        } else {
            utilisateur.setAgence(null);
        }

        return toDto(utilisateurRepository.save(utilisateur));
    }

    /**
     * Suppression contrôlée d'un compte.
     *
     * Un compte qui a déjà laissé des traces (auteur de mouvements, créateur
     * de campagnes, demandeur ou traiteur de demandes) ne se supprime pas —
     * la traçabilité exige de savoir qui a fait quoi. Plutôt qu'une erreur
     * 500 « foreign key », on renvoie un 409 invitant à désactiver le compte
     * (case « Compte actif »), ce qui bloque la connexion sans effacer l'histoire.
     */
    @Transactional
    public void deleteUtilisateur(Long id, Long currentUserId) {
        // Interdire la suppression de son propre compte
        if (id.equals(currentUserId)) {
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte.");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID : " + id));

        long mouvements = mouvementRepository.countByUtilisateurId(id);
        long campagnes = campagneRepository.countByUtilisateurId(id);
        long demandes = demandeRepository.countByDemandeurId(id) + demandeRepository.countByTraiteeParId(id);

        if (mouvements > 0 || campagnes > 0 || demandes > 0) {
            throw new ConflitMetierException(
                    "Impossible de supprimer le compte @" + utilisateur.getNomUtilisateur()
                    + " : il est référencé par " + mouvements + " mouvement(s), " + campagnes
                    + " campagne(s) et " + demandes + " demande(s). "
                    + "Désactivez-le plutôt (case « Compte actif ») pour conserver la traçabilité.");
        }

        utilisateurRepository.delete(utilisateur);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordDto dto) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'ID : " + id));

        // Vérifier l'ancien mot de passe
        if (!passwordEncoder.matches(dto.getAncienMotDePasse(), utilisateur.getMotDePasse())) {
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect.");
        }

        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        utilisateurRepository.save(utilisateur);
    }

    /**
     * Changement de son propre mot de passe (endpoint /api/auth/change-password).
     *
     * L'ancien endpoint /api/utilisateurs/{id}/change-password est réservé à
     * l'ADMIN mais exige l'ancien mot de passe : un employé ne pouvait donc
     * jamais changer le sien. Ce mode opératoire s'applique à l'utilisateur
     * AUTHENTIFIÉ, ce qui rend enfin la fonctionnalité utilisable.
     */
    @Transactional
    public void changerMotDePassePropre(Utilisateur utilisateur, ChangePasswordDto dto) {
        if (utilisateur == null) {
            throw new IllegalArgumentException("Authentification requise.");
        }
        if (!passwordEncoder.matches(dto.getAncienMotDePasse(), utilisateur.getMotDePasse())) {
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect.");
        }
        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        utilisateurRepository.save(utilisateur);
    }

    /**
     * Fiche complète du compte CONNECTÉ (GET /api/auth/profil).
     *
     * /utilisateurs/{id} est réservé à l'ADMIN : sans cet endpoint, un employé
     * ne pourrait jamais consulter sa propre fiche (email, téléphone, photo,
     * service, agence, date de création).
     */
    @Transactional(readOnly = true)
    public UtilisateurDto profilDe(Utilisateur utilisateur) {
        if (utilisateur == null) {
            throw new IllegalArgumentException("Authentification requise.");
        }
        return toDto(utilisateur);
    }

    /**
     * Mise à jour des coordonnées par leur TITULAIRE (PATCH /api/auth/profil).
     *
     * Email, téléphone et photo uniquement — nom, rôle, service et agence
     * restent des prérogatives de l'ADMIN. Un champ non transmis (ou vide)
     * reste inchangé, ce qui autorise une mise à jour ciblée (photo seule…).
     * Chaque modification réelle laisse une trace au journal.
     */
    @Transactional
    public UtilisateurDto mettreAJourProfil(Utilisateur utilisateur, ProfilDto dto) {
        if (utilisateur == null) {
            throw new IllegalArgumentException("Authentification requise.");
        }

        List<String> champsModifies = new ArrayList<>();

        String emailPropose = dto.getEmail() != null ? dto.getEmail().trim() : "";
        if (!emailPropose.isEmpty() && !emailPropose.equalsIgnoreCase(utilisateur.getEmail())) {
            if (utilisateurRepository.existsByEmail(emailPropose)) {
                throw new IllegalArgumentException("L'email '" + emailPropose + "' est déjà utilisé.");
            }
            utilisateur.setEmail(emailPropose);
            champsModifies.add("email");
        }

        if (dto.getTelephone() != null) {
            String telPropose = dto.getTelephone().trim();
            String telCourant = utilisateur.getTelephone() == null ? "" : utilisateur.getTelephone();
            if (!telPropose.equals(telCourant)) {
                utilisateur.setTelephone(telPropose.isEmpty() ? null : telPropose);
                champsModifies.add("téléphone");
            }
        }

        if (dto.getPhoto() != null && !dto.getPhoto().equals(utilisateur.getPhoto())) {
            utilisateur.setPhoto(dto.getPhoto());
            champsModifies.add("photo");
        }

        if (!champsModifies.isEmpty()) {
            utilisateurRepository.save(utilisateur);
            journalAuditMetier.enregistrer(utilisateur, JournalAuditMetier.MODIFICATION,
                    JournalAuditMetier.ENTITE_UTILISATEUR, utilisateur.getId(),
                    "Coordonnées du compte mises à jour (" + String.join(", ", champsModifies) + ")");
        }
        return toDto(utilisateur);
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> searchUtilisateurs(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllUtilisateurs();
        }
        return utilisateurRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private UtilisateurDto toDto(Utilisateur utilisateur) {
        UtilisateurDto dto = new UtilisateurDto();
        dto.setId(utilisateur.getId());
        dto.setNomUtilisateur(utilisateur.getNomUtilisateur());
        // ⚠️ NE PAS retourner le mot de passe !
        dto.setEmail(utilisateur.getEmail());
        dto.setNom(utilisateur.getNom());
        dto.setPrenom(utilisateur.getPrenom());
        dto.setTelephone(utilisateur.getTelephone());
        dto.setPhoto(utilisateur.getPhoto());
        dto.setRole(utilisateur.getRole() != null ? utilisateur.getRole().name() : null);
        dto.setActif(utilisateur.getActif());
        dto.setDateCreation(utilisateur.getDateCreation());

        if (utilisateur.getService() != null) {
            dto.setServiceId(utilisateur.getService().getId());
            dto.setServiceNom(utilisateur.getService().getNomService());
        }
        if (utilisateur.getAgence() != null) {
            dto.setAgenceId(utilisateur.getAgence().getId());
            dto.setAgenceNom(utilisateur.getAgence().getNomAgence());
        }
        return dto;
    }
}