package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.CategorieDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Categorie;
import com.auximad.gestion_immobilisation.repository.CategorieRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategorieMetier {

    private final CategorieRepository categorieRepository;
    private final ImmobilisationRepository immobilisationRepository;
    private final DemandeImmobilisationRepository demandeRepository;

    @Transactional(readOnly = true)
    public List<CategorieDto> getAllCategories() {
        return categorieRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategorieDto getCategorieById(Long id) {
        Categorie categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée avec l'ID : " + id));
        return toDto(categorie);
    }

    @Transactional
    public CategorieDto createCategorie(CategorieDto dto) {
        if (categorieRepository.existsByCodeComptable(dto.getCodeComptable())) {
            throw new IllegalArgumentException("Une catégorie avec le code comptable '" + dto.getCodeComptable() + "' existe déjà.");
        }

        Categorie categorie = Categorie.builder()
                .codeComptable(dto.getCodeComptable().trim())
                .nomCategorie(dto.getNomCategorie())
                .libelle(dto.getLibelle())
                .typeImmobilisation(dto.getTypeImmobilisation().toUpperCase())
                .classeCompte(dto.getClasseCompte())
                .tauxAmortissementParDefaut(dto.getTauxAmortissementParDefaut())
                .dureeUtilisation(dto.getDureeUtilisation())
                .actif(dto.getActif() != null ? dto.getActif() : true)
                .build();

        return toDto(categorieRepository.save(categorie));
    }

    @Transactional
    public CategorieDto updateCategorie(Long id, CategorieDto dto) {
        Categorie categorie = categorieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée avec l'ID : " + id));

        if (!categorie.getCodeComptable().equalsIgnoreCase(dto.getCodeComptable())
                && categorieRepository.existsByCodeComptable(dto.getCodeComptable())) {
            throw new IllegalArgumentException("Une catégorie avec le code comptable '" + dto.getCodeComptable() + "' existe déjà.");
        }

        categorie.setCodeComptable(dto.getCodeComptable().trim());
        categorie.setNomCategorie(dto.getNomCategorie());
        categorie.setLibelle(dto.getLibelle());
        categorie.setTypeImmobilisation(dto.getTypeImmobilisation().toUpperCase());
        categorie.setClasseCompte(dto.getClasseCompte());
        categorie.setTauxAmortissementParDefaut(dto.getTauxAmortissementParDefaut());
        categorie.setDureeUtilisation(dto.getDureeUtilisation());
        categorie.setActif(dto.getActif());

        return toDto(categorieRepository.save(categorie));
    }

    /**
     * Suppression contrôlée d'une catégorie : refusée (409) tant que des biens
     * ou des demandes la référencent (le code comptable porte l'identité
     * comptable du parc).
     */
    @Transactional
    public void deleteCategorie(Long id) {
        if (!categorieRepository.existsById(id)) {
            throw new ResourceNotFoundException("Catégorie non trouvée avec l'ID : " + id);
        }

        long immobilisations = immobilisationRepository.countByCategorieId(id);
        long demandes = demandeRepository.countByCategorieId(id);

        if (immobilisations > 0 || demandes > 0) {
            throw new ConflitMetierException(
                    "Impossible de supprimer cette catégorie : elle est référencée par "
                    + immobilisations + " immobilisation(s) et " + demandes + " demande(s). "
                    + "Désactivez-la plutôt (champ « actif »).");
        }

        categorieRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CategorieDto> searchCategories(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllCategories();
        }
        return categorieRepository.searchByKeyword(keyword.trim())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CategorieDto toDto(Categorie categorie) {
        CategorieDto dto = new CategorieDto();
        dto.setId(categorie.getId());
        dto.setCodeComptable(categorie.getCodeComptable());
        dto.setNomCategorie(categorie.getNomCategorie());
        dto.setLibelle(categorie.getLibelle());
        dto.setTypeImmobilisation(categorie.getTypeImmobilisation());
        dto.setClasseCompte(categorie.getClasseCompte());
        dto.setTauxAmortissementParDefaut(categorie.getTauxAmortissementParDefaut());
        dto.setDureeUtilisation(categorie.getDureeUtilisation());
        dto.setActif(categorie.getActif());
        dto.setDateCreation(categorie.getDateCreation());
        return dto;
    }
}