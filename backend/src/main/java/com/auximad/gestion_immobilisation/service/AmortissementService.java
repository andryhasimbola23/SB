package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.AmortissementDto;
import com.auximad.gestion_immobilisation.dto.AmortissementPlanDto;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Amortissement;
import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.repository.AmortissementRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AmortissementService {

    private final AmortissementRepository amortissementRepository;
    private final ImmobilisationRepository immobilisationRepository;

    /**
     * Calcule et sauvegarde le plan d'amortissement linéaire pour une immobilisation.
     */
    @Transactional
    public AmortissementPlanDto calculerPlanAmortissement(Long immobilisationId) {
        Immobilisation immo = immobilisationRepository.findById(immobilisationId)
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        // Supprimer l'ancien plan s'il existe
        amortissementRepository.deleteByImmobilisationId(immobilisationId);
        amortissementRepository.flush();

        BigDecimal valeurAchat = immo.getValeurAchat();
        BigDecimal taux = immo.getTauxAmortissement();

        // Calcul de la dotation annuelle
        BigDecimal dotationAnnuelle = valeurAchat
                .multiply(taux)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Durée d'amortissement = 100 / taux (arrondi supérieur)
        int dureeAmortissement = taux.compareTo(BigDecimal.ZERO) > 0
                ? (int) Math.ceil(100.0 / taux.doubleValue())
                : 0;

        // Année de début = année de mise en service
        int anneeDebut = immo.getDateMiseService().getYear();

        List<Amortissement> lignes = new ArrayList<>();
        BigDecimal cumul = BigDecimal.ZERO;

        for (int i = 0; i < dureeAmortissement; i++) {
            int annee = anneeDebut + i;
            cumul = cumul.add(dotationAnnuelle);

            // La dernière année, on ajuste pour ne pas dépasser la valeur d'achat
            if (cumul.compareTo(valeurAchat) > 0) {
                cumul = valeurAchat;
            }

            BigDecimal vnc = valeurAchat.subtract(cumul);

            Amortissement ligne = Amortissement.builder()
                    .annee(annee)
                    .valeurInitiale(valeurAchat)
                    .dotationAnnuelle(dotationAnnuelle)
                    .amortissementCumule(cumul)
                    .valeurNetteComptable(vnc)
                    .immobilisation(immo)
                    .build();

            lignes.add(ligne);
        }

        List<Amortissement> saved = amortissementRepository.saveAll(lignes);
        return buildPlanDto(immo, saved);
    }

    /**
     * Récupère le plan d'amortissement existant, ou le calcule s'il n'existe pas.
     */
    @Transactional
    public AmortissementPlanDto getPlanAmortissement(Long immobilisationId) {
        Immobilisation immo = immobilisationRepository.findById(immobilisationId)
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        List<Amortissement> lignes = amortissementRepository
                .findByImmobilisationIdOrderByAnneeAsc(immobilisationId);

        // Si aucun plan n'existe, on le calcule
        if (lignes.isEmpty()) {
            return calculerPlanAmortissement(immobilisationId);
        }

        return buildPlanDto(immo, lignes);
    }

    /**
     * Calcule la VNC à une date donnée.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculerVncActuelle(Long immobilisationId) {
        Immobilisation immo = immobilisationRepository.findById(immobilisationId)
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        return calculerVnc(immo.getValeurAchat(), immo.getTauxAmortissement(), immo.getDateMiseService());
    }

    /**
     * Calcul PUR de la VNC : ne dépend que des trois champs de la fiche.
     *
     * Extrait de {@link #calculerVncActuelle(Long)} pour permettre au tableau
     * de bord de sommer les VNC de tout le parc en une seule requête de
     * projection, au lieu d'un findById par bien actif (N+1).
     */
    public static BigDecimal calculerVnc(BigDecimal valeurAchat, BigDecimal tauxAmortissement,
                                         LocalDateTime dateMiseService) {
        int anneeActuelle = LocalDate.now().getYear();
        int anneeMiseEnService = dateMiseService.getYear();

        // Nombre d'années complètes d'amortissement
        int anneesEcoulees = Math.max(0, anneeActuelle - anneeMiseEnService);

        BigDecimal dotationAnnuelle = valeurAchat
                .multiply(tauxAmortissement)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal cumul = dotationAnnuelle.multiply(new BigDecimal(anneesEcoulees));

        // Ne pas dépasser la valeur d'achat
        if (cumul.compareTo(valeurAchat) > 0) {
            cumul = valeurAchat;
        }

        return valeurAchat.subtract(cumul);
    }

    /**
     * Supprime le plan d'amortissement d'une immobilisation.
     */
    @Transactional
    public void supprimerPlan(Long immobilisationId) {
        amortissementRepository.deleteByImmobilisationId(immobilisationId);
    }

    private AmortissementPlanDto buildPlanDto(Immobilisation immo, List<Amortissement> lignes) {
        AmortissementPlanDto dto = new AmortissementPlanDto();
        dto.setImmobilisationId(immo.getId());
        dto.setCodeUnique(immo.getCodeUnique());
        dto.setDesignation(immo.getDesignation());
        dto.setValeurAchat(immo.getValeurAchat());
        dto.setTauxAmortissement(immo.getTauxAmortissement());

        int duree = immo.getTauxAmortissement().compareTo(BigDecimal.ZERO) > 0
                ? (int) Math.ceil(100.0 / immo.getTauxAmortissement().doubleValue())
                : 0;
        dto.setDureeAmortissement(duree);

        // VNC actuelle (dernière ligne dont l'année <= année actuelle)
        int anneeActuelle = LocalDate.now().getYear();
        BigDecimal vncActuelle = immo.getValeurAchat();
        for (Amortissement ligne : lignes) {
            if (ligne.getAnnee() <= anneeActuelle) {
                vncActuelle = ligne.getValeurNetteComptable();
            }
        }
        dto.setVncActuelle(vncActuelle);

        dto.setLignes(lignes.stream().map(this::toDto).collect(Collectors.toList()));
        return dto;
    }

    private AmortissementDto toDto(Amortissement a) {
        AmortissementDto dto = new AmortissementDto();
        dto.setId(a.getId());
        dto.setAnnee(a.getAnnee());
        dto.setValeurInitiale(a.getValeurInitiale());
        dto.setDotationAnnuelle(a.getDotationAnnuelle());
        dto.setAmortissementCumule(a.getAmortissementCumule());
        dto.setValeurNetteComptable(a.getValeurNetteComptable());
        return dto;
    }
}