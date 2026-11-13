package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.AgenceDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.repository.AgenceRepository;
import com.auximad.gestion_immobilisation.repository.CampagneInventaireRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgenceService {

    private final AgenceRepository agenceRepository;
    private final ImmobilisationRepository immobilisationRepository;
    private final ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CampagneInventaireRepository campagneRepository;

    /**
     * Récupère toutes les agences.
     */
    @Transactional(readOnly = true)
    public List<AgenceDto> getAllAgences() {
        return agenceRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Récupère une agence par son ID.
     */
    @Transactional(readOnly = true)
    public AgenceDto getAgenceById(Long id) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée avec l'ID : " + id));
        return toDto(agence);
    }

    /**
     * Crée une nouvelle agence.
     */
    @Transactional
    public AgenceDto createAgence(AgenceDto dto) {
        // Vérifier si le code existe déjà
        if (agenceRepository.existsByCodeAgence(dto.getCodeAgence())) {
            throw new IllegalArgumentException("Une agence avec le code '" + dto.getCodeAgence() + "' existe déjà.");
        }

        Agence agence = Agence.builder()
                .codeAgence(dto.getCodeAgence().toUpperCase())
                .nomAgence(dto.getNomAgence())
                .villeAgence(dto.getVilleAgence())
                .emailAgence(dto.getEmailAgence())
                .telephoneAgence(dto.getTelephoneAgence())
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .build();

        return toDto(agenceRepository.save(agence));
    }

    /**
     * Met à jour une agence existante.
     */
    @Transactional
    public AgenceDto updateAgence(Long id, AgenceDto dto) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée avec l'ID : " + id));

        // Vérifier si le nouveau code n'est pas déjà utilisé par une autre agence
        if (!agence.getCodeAgence().equalsIgnoreCase(dto.getCodeAgence())
                && agenceRepository.existsByCodeAgence(dto.getCodeAgence())) {
            throw new IllegalArgumentException("Une agence avec le code '" + dto.getCodeAgence() + "' existe déjà.");
        }

        agence.setCodeAgence(dto.getCodeAgence().toUpperCase());
        agence.setNomAgence(dto.getNomAgence());
        agence.setVilleAgence(dto.getVilleAgence());
        agence.setEmailAgence(dto.getEmailAgence());
        agence.setTelephoneAgence(dto.getTelephoneAgence());
        agence.setActif(dto.getActif());

        return toDto(agenceRepository.save(agence));
    }

    /**
     * Suppression contrôlée d'une agence : refusée (409) tant qu'elle est
     * encore référencée, plutôt qu'une erreur 500 « foreign key ».
     * La parade en production est la désactivation (champ « actif »).
     */
    @Transactional
    public void deleteAgence(Long id) {
        if (!agenceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Agence non trouvée avec l'ID : " + id);
        }

        long immobilisations = immobilisationRepository.countByAgenceId(id);
        long services = serviceRepository.countByAgenceId(id);
        long utilisateurs = utilisateurRepository.countByAgenceId(id);
        long campagnes = campagneRepository.countByAgenceId(id);

        if (immobilisations > 0 || services > 0 || utilisateurs > 0 || campagnes > 0) {
            throw new ConflitMetierException(
                    "Impossible de supprimer cette agence : elle est référencée par "
                    + immobilisations + " immobilisation(s), " + services + " service(s), "
                    + utilisateurs + " utilisateur(s) et " + campagnes + " campagne(s) d'inventaire. "
                    + "Désactivez-la plutôt (champ « actif »).");
        }

        agenceRepository.deleteById(id);
    }

    /**
     * Recherche des agences par mot-clé (nom, code, ville).
     */
    @Transactional(readOnly = true)
    public List<AgenceDto> searchAgences(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllAgences();
        }
        return agenceRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Convertit une entité Agence en DTO.
     */
    private AgenceDto toDto(Agence agence) {
        AgenceDto dto = new AgenceDto();
        dto.setId(agence.getId());
        dto.setCodeAgence(agence.getCodeAgence());
        dto.setNomAgence(agence.getNomAgence());
        dto.setVilleAgence(agence.getVilleAgence());
        dto.setEmailAgence(agence.getEmailAgence());
        dto.setTelephoneAgence(agence.getTelephoneAgence());
        dto.setActif(agence.getActif());
        dto.setDateCreation(agence.getDateCreation());
        return dto;
    }
}