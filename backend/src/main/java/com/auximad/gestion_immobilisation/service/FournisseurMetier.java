package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.FournisseurDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Fournisseur;
import com.auximad.gestion_immobilisation.repository.FournisseurRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FournisseurMetier {

    private final FournisseurRepository fournisseurRepository;
    private final ImmobilisationRepository immobilisationRepository;

    @Transactional(readOnly = true)
    public List<FournisseurDto> getAllFournisseurs() {
        return fournisseurRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FournisseurDto getFournisseurById(Long id) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur non trouvé avec l'ID : " + id));
        return toDto(fournisseur);
    }

    @Transactional
    public FournisseurDto createFournisseur(FournisseurDto dto) {
        Fournisseur fournisseur = Fournisseur.builder()
                .nomFournisseur(dto.getNomFournisseur().trim())
                .adresseFournisseur(dto.getAdresseFournisseur())
                .telephoneFournisseur(dto.getTelephoneFournisseur())
                .emailFournisseur(dto.getEmailFournisseur())
                .siteWeb(dto.getSiteWeb())
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .build();

        return toDto(fournisseurRepository.save(fournisseur));
    }

    @Transactional
    public FournisseurDto updateFournisseur(Long id, FournisseurDto dto) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur non trouvé avec l'ID : " + id));

        fournisseur.setNomFournisseur(dto.getNomFournisseur().trim());
        fournisseur.setAdresseFournisseur(dto.getAdresseFournisseur());
        fournisseur.setTelephoneFournisseur(dto.getTelephoneFournisseur());
        fournisseur.setEmailFournisseur(dto.getEmailFournisseur());
        fournisseur.setSiteWeb(dto.getSiteWeb());
        fournisseur.setActif(dto.getActif());

        return toDto(fournisseurRepository.save(fournisseur));
    }

    /**
     * Suppression contrôlée d'un fournisseur : refusée (409) tant que des biens
     * l'achètent encore (l'historique des acquisitions en dépend).
     */
    @Transactional
    public void deleteFournisseur(Long id) {
        if (!fournisseurRepository.existsById(id)) {
            throw new ResourceNotFoundException("Fournisseur non trouvé avec l'ID : " + id);
        }

        long immobilisations = immobilisationRepository.countByFournisseurId(id);

        if (immobilisations > 0) {
            throw new ConflitMetierException(
                    "Impossible de supprimer ce fournisseur : " + immobilisations
                    + " immobilisation(s) ont été achetées chez lui. "
                    + "Désactivez-le plutôt (champ « actif »).");
        }

        fournisseurRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<FournisseurDto> searchFournisseurs(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllFournisseurs();
        }
        return fournisseurRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private FournisseurDto toDto(Fournisseur fournisseur) {
        FournisseurDto dto = new FournisseurDto();
        dto.setId(fournisseur.getId());
        dto.setNomFournisseur(fournisseur.getNomFournisseur());
        dto.setAdresseFournisseur(fournisseur.getAdresseFournisseur());
        dto.setTelephoneFournisseur(fournisseur.getTelephoneFournisseur());
        dto.setEmailFournisseur(fournisseur.getEmailFournisseur());
        dto.setSiteWeb(fournisseur.getSiteWeb());
        dto.setActif(fournisseur.getActif());
        dto.setDateCreation(fournisseur.getDateCreation());
        return dto;
    }
}