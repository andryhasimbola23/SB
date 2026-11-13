package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.CampagneInventaireDto;
import com.auximad.gestion_immobilisation.dto.LigneInventaireDto;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.*;
import com.auximad.gestion_immobilisation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventaireMetier {

    private final CampagneInventaireRepository campagneRepository;
    private final LigneInventaireRepository ligneRepository;
    private final ImmobilisationRepository immobilisationRepository;
    private final AgenceRepository agenceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationMetier notificationMetier;
    private final JournalAuditMetier journalAuditMetier;

    // ============ CAMPAGNES ============

    /**
     * Liste des campagnes avec leurs statistiques.
     *
     * Les stats de TOUTES les campagnes sont calculées en 2 requêtes
     * (GROUP BY) + 1 pour les totaux par agence — l'ancienne version
     * exécutait 4 compteurs PAR campagne (N+1) et comptait le parc entier
     * même pour une campagne restreinte à une agence.
     */
    @Transactional(readOnly = true)
    public List<CampagneInventaireDto> getAllCampagnes() {
        List<CampagneInventaire> campagnes = campagneRepository.findAllByOrderByDateCreationDesc();
        if (campagnes.isEmpty()) {
            return List.of();
        }

        // Constats par campagne et par état, en une seule requête
        List<Long> ids = campagnes.stream()
                .map(CampagneInventaire::getId)
                .collect(Collectors.toList());
        Map<Long, Map<String, Long>> statsParCampagne = new HashMap<>();
        for (Object[] r : ligneRepository.compterParCampagneEtEtat(ids)) {
            statsParCampagne
                    .computeIfAbsent((Long) r[0], k -> new HashMap<>())
                    .put((String) r[1], ((Number) r[2]).longValue());
        }

        // Taille du parc concerné : global si la campagne couvre toutes les
        // agences, sinon le parc de l'agence de la campagne.
        long totalParc = immobilisationRepository.count();
        Map<Long, Long> totauxParAgence = new HashMap<>();
        List<Long> agenceIds = campagnes.stream()
                .map(CampagneInventaire::getAgence)
                .filter(Objects::nonNull)
                .map(Agence::getId)
                .distinct()
                .collect(Collectors.toList());
        if (!agenceIds.isEmpty()) {
            for (Object[] r : immobilisationRepository.compterParAgence(agenceIds)) {
                totauxParAgence.put((Long) r[0], ((Number) r[1]).longValue());
            }
        }

        return campagnes.stream()
                .map(c -> {
                    Map<String, Long> stats = statsParCampagne.getOrDefault(c.getId(), Map.of());
                    long totalImmos = c.getAgence() == null
                            ? totalParc
                            : totauxParAgence.getOrDefault(c.getAgence().getId(), 0L);
                    return toCampagneDto(c, stats, totalImmos);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampagneInventaireDto getCampagneById(Long id) {
        CampagneInventaire c = campagneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campagne non trouvée"));
        return toCampagneDto(c);
    }

    @Transactional
    public CampagneInventaireDto createCampagne(CampagneInventaireDto dto, Long utilisateurId) {
        CampagneInventaire campagne = CampagneInventaire.builder()
                .libelle(dto.getLibelle())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .statut("EN_COURS")
                .build();

        if (dto.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(dto.getAgenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée"));
            campagne.setAgence(agence);
        }

        if (utilisateurId != null) {
            Utilisateur u = new Utilisateur();
            u.setId(utilisateurId);
            campagne.setUtilisateur(u);
        }

        return toCampagneDto(campagneRepository.save(campagne));
    }

    @Transactional
    public CampagneInventaireDto updateCampagne(Long id, CampagneInventaireDto dto) {
        CampagneInventaire campagne = campagneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campagne non trouvée"));

        campagne.setLibelle(dto.getLibelle());
        campagne.setDateDebut(dto.getDateDebut());
        campagne.setDateFin(dto.getDateFin());

        if (dto.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(dto.getAgenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée"));
            campagne.setAgence(agence);
        } else {
            campagne.setAgence(null);
        }

        return toCampagneDto(campagneRepository.save(campagne));
    }

    @Transactional
    public CampagneInventaireDto cloturerCampagne(Long id, Long acteurId) {
        CampagneInventaire campagne = campagneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campagne non trouvée"));

        campagne.setStatut("CLOTURE");
        if (campagne.getDateFin() == null) {
            campagne.setDateFin(LocalDate.now());
        }

        CampagneInventaireDto dto = toCampagneDto(campagneRepository.save(campagne));

        // Journal d'audit : la clôture fige les constats — trace qui va de pair
        // avec l'arbitrage des écarts.
        journalAuditMetier.enregistrer(
                acteurId != null ? utilisateurRepository.findById(acteurId).orElse(null) : null,
                JournalAuditMetier.CLOTURE_CAMPAGNE, JournalAuditMetier.ENTITE_CAMPAGNE, id,
                "Campagne « " + campagne.getLibelle() + " » clôturée");

        // In-app : le créateur de la campagne apprend sa clôture (sauf s'il
        // la clôture lui-même).
        if (campagne.getUtilisateur() != null) {
            notificationMetier.notifier(
                    campagne.getUtilisateur().getId(),
                    NotificationMetier.CAMPAGNE_CLOTUREE,
                    "Campagne clôturée : " + campagne.getLibelle(),
                    "La campagne d'inventaire est terminée — les écarts restants sont à arbitrer.",
                    "/inventaires",
                    acteurId);
        }

        return dto;
    }

    @Transactional
    public void deleteCampagne(Long id) {
        if (!campagneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Campagne non trouvée");
        }
        campagneRepository.deleteById(id);
    }

    // ============ LIGNES D'INVENTAIRE ============

    @Transactional(readOnly = true)
    public List<LigneInventaireDto> getLignesByCampagne(Long campagneId) {
        return ligneRepository.findByCampagneIdOrderByDateScanDesc(campagneId)
                .stream()
                .map(this::toLigneDto)
                .collect(Collectors.toList());
    }

    /**
     * Enregistre un scan d'inventaire.
     * ✅ SYNCHRONISATION AUTOMATIQUE : l'état physique de l'immobilisation
     * est mis à jour selon l'état constaté pendant l'inventaire.
     */
    @Transactional
    public LigneInventaireDto scannerImmobilisation(LigneInventaireDto dto) {
        CampagneInventaire campagne = campagneRepository.findById(dto.getCampagneId())
                .orElseThrow(() -> new ResourceNotFoundException("Campagne non trouvée"));

        if ("CLOTURE".equals(campagne.getStatut())) {
            throw new IllegalArgumentException("Impossible de scanner : la campagne est clôturée.");
        }

        Immobilisation immo = immobilisationRepository.findById(dto.getImmobilisationId())
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        // Chercher une ligne existante pour cette campagne + immobilisation
        LigneInventaire ligne = ligneRepository
                .findByCampagneIdAndImmobilisationId(campagne.getId(), immo.getId())
                .orElse(LigneInventaire.builder()
                        .campagne(campagne)
                        .immobilisation(immo)
                        .build());

        ligne.setDateScan(LocalDateTime.now());
        ligne.setLocalisationConstatee(dto.getLocalisationConstatee());
        ligne.setEtatConstate(dto.getEtatConstate() != null ? dto.getEtatConstate() : "PRESENT");
        ligne.setObservation(dto.getObservation());

        LigneInventaire saved = ligneRepository.save(ligne);

        // ✅ SYNCHRONISATION AUTOMATIQUE DE L'ÉTAT DE L'IMMOBILISATION
        synchroniserEtatImmobilisation(immo, dto.getEtatConstate());

        return toLigneDto(saved);
    }

    /**
     * Synchronise l'état physique de l'immobilisation selon l'état constaté en inventaire.
     * Cette méthode garantit que la liste des immobilisations reflète la réalité physique.
     *
     * Mapping :
     * - PRESENT      → BON
     * - MAUVAIS_ETAT → MAUVAIS
     * - MANQUANT     → HORS_SERVICE
     */
    private void synchroniserEtatImmobilisation(Immobilisation immo, String etatInventaire) {
        if (etatInventaire == null) return;

        String nouvelEtat = null;

        switch (etatInventaire) {
            case "PRESENT":
                nouvelEtat = "BON";
                break;
            case "MAUVAIS_ETAT":
                nouvelEtat = "MAUVAIS";
                break;
            case "MANQUANT":
                nouvelEtat = "HORS_SERVICE";
                break;
            default:
                return;
        }

        // Mettre à jour uniquement si l'état a changé
        if (!nouvelEtat.equals(immo.getEtatPhysique())) {
            immo.setEtatPhysique(nouvelEtat);
            immobilisationRepository.save(immo);
        }
    }

    @Transactional
    public void deleteLigne(Long id) {
        if (!ligneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ligne non trouvée");
        }
        ligneRepository.deleteById(id);
    }

    // ============ STATISTIQUES ============

    /**
     * DTO d'une campagne seule (création, mise à jour, clôture) : les
     * statistiques sont recalculées à la volée, campagne par campagne.
     */
    private CampagneInventaireDto toCampagneDto(CampagneInventaire c) {
        Map<String, Long> stats = new HashMap<>();
        if (c.getId() != null) {
            stats.put("PRESENT", ligneRepository.countByCampagneIdAndEtatConstate(c.getId(), "PRESENT"));
            stats.put("MANQUANT", ligneRepository.countByCampagneIdAndEtatConstate(c.getId(), "MANQUANT"));
            stats.put("MAUVAIS_ETAT", ligneRepository.countByCampagneIdAndEtatConstate(c.getId(), "MAUVAIS_ETAT"));
        }
        long totalImmos = c.getAgence() != null
                ? immobilisationRepository.countByAgenceId(c.getAgence().getId())
                : immobilisationRepository.count();
        return toCampagneDto(c, stats, totalImmos);
    }

    private CampagneInventaireDto toCampagneDto(CampagneInventaire c,
                                                Map<String, Long> stats,
                                                long totalImmobilisations) {
        CampagneInventaireDto dto = new CampagneInventaireDto();
        dto.setId(c.getId());
        dto.setLibelle(c.getLibelle());
        dto.setDateDebut(c.getDateDebut());
        dto.setDateFin(c.getDateFin());
        dto.setStatut(c.getStatut());
        dto.setDateCreation(c.getDateCreation());

        if (c.getAgence() != null) {
            dto.setAgenceId(c.getAgence().getId());
            dto.setAgenceNom(c.getAgence().getNomAgence());
            dto.setAgenceCode(c.getAgence().getCodeAgence());
        }
        if (c.getUtilisateur() != null) {
            dto.setUtilisateurId(c.getUtilisateur().getId());
            dto.setUtilisateurNom(c.getUtilisateur().getPrenom() + " " + c.getUtilisateur().getNom());
        }

        // Statistiques (les états constatés couvrent toutes les lignes)
        long totalScannees = stats.values().stream().mapToLong(Long::longValue).sum();

        dto.setTotalImmobilisations(totalImmobilisations);
        dto.setTotalScannees(totalScannees);
        dto.setTotalPresentes(stats.getOrDefault("PRESENT", 0L));
        dto.setTotalManquantes(stats.getOrDefault("MANQUANT", 0L));
        dto.setTotalMauvaisEtat(stats.getOrDefault("MAUVAIS_ETAT", 0L));

        return dto;
    }

    private LigneInventaireDto toLigneDto(LigneInventaire l) {
        LigneInventaireDto dto = new LigneInventaireDto();
        dto.setId(l.getId());
        dto.setDateScan(l.getDateScan());
        dto.setLocalisationConstatee(l.getLocalisationConstatee());
        dto.setEtatConstate(l.getEtatConstate());
        dto.setObservation(l.getObservation());
        dto.setCampagneId(l.getCampagne() != null ? l.getCampagne().getId() : null);

        if (l.getImmobilisation() != null) {
            dto.setImmobilisationId(l.getImmobilisation().getId());
            dto.setImmobilisationCode(l.getImmobilisation().getCodeUnique());
            dto.setImmobilisationDesignation(l.getImmobilisation().getDesignation());
            dto.setImmobilisationPhoto(l.getImmobilisation().getPhoto());
            dto.setImmobilisationEtatTheorique(l.getImmobilisation().getEtatPhysique());

            if (l.getImmobilisation().getService() != null) {
                dto.setImmobilisationService(l.getImmobilisation().getService().getNomService());
            }
            if (l.getImmobilisation().getAgence() != null) {
                dto.setImmobilisationAgence(l.getImmobilisation().getAgence().getNomAgence());
            }
        }
        return dto;
    }
}