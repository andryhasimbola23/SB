package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.DashboardStatsDto;
import com.auximad.gestion_immobilisation.dto.MouvementDto;
import com.auximad.gestion_immobilisation.dto.RepartitionDto;
import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.model.Mouvement;
import com.auximad.gestion_immobilisation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardMetier {

    private final ImmobilisationRepository immobilisationRepository;
    private final MouvementRepository mouvementRepository;
    private final CampagneInventaireRepository campagneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AgenceRepository agenceRepository;
    private final ServiceRepository serviceRepository;
    private final CategorieRepository categorieRepository;
    private final FournisseurRepository fournisseurRepository;

    /**
     * Récupère toutes les statistiques principales du tableau de bord.
     *
     * @param serviceId périmètre de l'utilisateur (null = ADMIN).
     */
    @Transactional(readOnly = true)
    public DashboardStatsDto getStats(Long serviceId) {
        LocalDateTime maintenant = LocalDateTime.now();
        int moisActuel = maintenant.getMonthValue();
        int anneeActuelle = maintenant.getYear();

        long total = immobilisationRepository.countDansPerimetre(serviceId);
        long actifs = immobilisationRepository.countByStatut("ACTIF", serviceId);
        long cedes = immobilisationRepository.countByStatut("CEDE", serviceId);
        long rebutes = immobilisationRepository.countByStatut("REBUTE", serviceId);
        long mauvaisEtat = immobilisationRepository.countByEtatPhysique("MAUVAIS", serviceId);
        long horsService = immobilisationRepository.countByEtatPhysique("HORS_SERVICE", serviceId);

        BigDecimal valeurTotale = immobilisationRepository.getValeurTotaleParc(serviceId);
        if (valeurTotale == null) valeurTotale = BigDecimal.ZERO;

        BigDecimal vncTotale = calculerVncTotale(serviceId);

        long mouvementsMois = mouvementRepository.countByMoisAndAnnee(moisActuel, anneeActuelle, serviceId);
        long nouveauxBiens = immobilisationRepository.countNouveauxBiens(moisActuel, anneeActuelle, serviceId);

        // Les campagnes d'inventaire sont exclusives à l'ADMIN : un employé
        // n'en doit connaître ni le nombre ni l'existence.
        long campagnesEnCours = serviceId == null
                ? campagneRepository.countByStatut("EN_COURS")
                : 0L;

        // Part amortie du parc : mesure de l'âge comptable du parc, plus
        // parlante que deux montants bruts juxtaposés.
        BigDecimal tauxAmortissement = tauxAmortissementGlobal(valeurTotale, vncTotale);

        // Parc vieillissant : actifs de plus de 5 ans — au-delà, la question
        // du renouvellement se pose.
        long biensAnciens = immobilisationRepository
                .countActifsAnciens(maintenant.minusYears(5), serviceId);

        return DashboardStatsDto.builder()
                .totalImmobilisations(total)
                .totalActifs(actifs)
                .totalCedes(cedes)
                .totalRebutes(rebutes)
                .totalMauvaisEtat(mauvaisEtat)
                .totalHorsService(horsService)
                .valeurTotaleParc(valeurTotale)
                .vncTotale(vncTotale)
                .mouvementsCeMois(mouvementsMois)
                .nouveauxBiensCeMois(nouveauxBiens)
                .campagnesEnCours(campagnesEnCours)
                .tauxAmortissementGlobal(tauxAmortissement)
                .biensAnciens(biensAnciens)
                // Référentiel : volontairement non filtré par le périmètre —
                // agences, services, catégories, fournisseurs et comptes
                // d'utilisateurs décrivent l'entreprise, pas le parc d'un service.
                .totalUtilisateurs(utilisateurRepository.count())
                .totalAgences(agenceRepository.count())
                .totalServices(serviceRepository.count())
                .totalCategories(categorieRepository.count())
                .totalFournisseurs(fournisseurRepository.count())
                .build();
    }

    /**
     * Répartition des biens par catégorie.
     */
    @Transactional(readOnly = true)
    public List<RepartitionDto> getRepartitionParCategorie(Long serviceId) {
        List<Object[]> rows = immobilisationRepository.getRepartitionParCategorie(serviceId);
        return rows.stream()
                .map(r -> new RepartitionDto(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO
                ))
                .collect(Collectors.toList());
    }

    /**
     * Répartition des biens par service.
     *
     * La requête renvoie [sigle, nom, nombre, valeur] : le nom sert de libellé,
     * le sigle d'axe de graphique.
     */
    @Transactional(readOnly = true)
    public List<RepartitionDto> getRepartitionParService(Long serviceId) {
        List<Object[]> rows = immobilisationRepository.getRepartitionParService(serviceId);
        return rows.stream()
                .map(r -> new RepartitionDto(
                        (String) r[1],
                        (String) r[0],
                        ((Number) r[2]).longValue(),
                        r[3] != null ? (BigDecimal) r[3] : BigDecimal.ZERO
                ))
                .collect(Collectors.toList());
    }

    /**
     * Valeur du parc par agence (biens actifs) — ADMIN.
     *
     * La requête renvoie [nomAgence, nombre, valeur] : le nom fait office de
     * libellé comme d'axe (les noms d'agences sont courts).
     */
    @Transactional(readOnly = true)
    public List<RepartitionDto> getRepartitionParAgence(Long serviceId) {
        List<Object[]> rows = immobilisationRepository.getRepartitionParAgence(serviceId);
        return rows.stream()
                .map(r -> new RepartitionDto(
                        (String) r[0],
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO
                ))
                .collect(Collectors.toList());
    }

    /**
     * Part déjà amortie du parc actif, en pourcentage à une décimale.
     *
     * Bornée à [0, 100] : une VNC supérieure à la valeur d'achat (bien très
     * récent, taux saisi trop bas) donnerait un « amorti » négatif, qui
     * n'a aucun sens comptable à afficher.
     */
    private BigDecimal tauxAmortissementGlobal(BigDecimal valeurAcquisition, BigDecimal vnc) {
        if (valeurAcquisition == null || valeurAcquisition.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal part = BigDecimal.ONE
                .subtract(vnc.divide(valeurAcquisition, 4, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        return part.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    /**
     * Les 5 derniers mouvements.
     */
    @Transactional(readOnly = true)
    public List<MouvementDto> getDerniersMouvements(Long serviceId) {
        return mouvementRepository.findDerniersMouvements(serviceId)
                .stream()
                .map(this::toMouvementDto)
                .collect(Collectors.toList());
    }

    /**
     * Les 5 dernières acquisitions.
     */
    @Transactional(readOnly = true)
    public List<com.auximad.gestion_immobilisation.dto.ImmobilisationDto> getDernieresAcquisitions(Long serviceId) {
        return immobilisationRepository.getDernieresAcquisitions(serviceId)
                .stream()
                .map(this::toImmobilisationDto)
                .collect(Collectors.toList());
    }

    // ============ HELPERS ============

    /**
     * VNC totale du parc, en UNE requête de projection.
     *
     * L'ancienne version chargeait tous les actifs puis rappelait
     * calculerVncActuelle pour chacun — un findById par bien (N+1), lent dès
     * quelques centaines d'immobilisations. La formule ne dépend que de
     * (valeurAchat, taux, dateMiseService) : une requête projection suffit,
     * le calcul se faisant en mémoire sur trois colonnes.
     */
    private BigDecimal calculerVncTotale(Long serviceId) {
        List<Object[]> donnees = immobilisationRepository.findDonneesVncDansPerimetre(serviceId);
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] d : donnees) {
            BigDecimal valeurAchat = (BigDecimal) d[0];
            BigDecimal taux = (BigDecimal) d[1];
            LocalDateTime dateMiseService = (LocalDateTime) d[2];

            if (valeurAchat == null || taux == null || dateMiseService == null) {
                // Bien incomplet : au moins la valeur d'achat est conservée.
                if (valeurAchat != null) total = total.add(valeurAchat);
                continue;
            }
            total = total.add(AmortissementService.calculerVnc(valeurAchat, taux, dateMiseService));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private MouvementDto toMouvementDto(Mouvement mvt) {
        MouvementDto dto = new MouvementDto();
        dto.setId(mvt.getId());
        dto.setTypeMouvement(mvt.getTypeMouvement());
        dto.setDateMouvement(mvt.getDateMouvement());
        dto.setAncienneLocalisation(mvt.getAncienneLocalisation());
        dto.setNouvelleLocalisation(mvt.getNouvelleLocalisation());
        dto.setValeurVnc(mvt.getValeurVnc());
        dto.setCommentaire(mvt.getCommentaire());
        dto.setPrixVente(mvt.getPrixVente());
        dto.setPlusMoinsValue(mvt.getPlusMoinsValue());

        if (mvt.getImmobilisation() != null) {
            dto.setImmobilisationId(mvt.getImmobilisation().getId());
            dto.setImmobilisationCode(mvt.getImmobilisation().getCodeUnique());
            dto.setImmobilisationDesignation(mvt.getImmobilisation().getDesignation());
        }
        if (mvt.getUtilisateur() != null) {
            dto.setUtilisateurNom(mvt.getUtilisateur().getPrenom() + " " + mvt.getUtilisateur().getNom());
        }
        return dto;
    }

    private com.auximad.gestion_immobilisation.dto.ImmobilisationDto toImmobilisationDto(Immobilisation immo) {
        com.auximad.gestion_immobilisation.dto.ImmobilisationDto dto =
                new com.auximad.gestion_immobilisation.dto.ImmobilisationDto();
        dto.setId(immo.getId());
        dto.setCodeUnique(immo.getCodeUnique());
        dto.setDesignation(immo.getDesignation());
        dto.setValeurAchat(immo.getValeurAchat());
        dto.setStatut(immo.getStatut());
        dto.setEtatPhysique(immo.getEtatPhysique());
        dto.setPhoto(immo.getPhoto());
        dto.setTypeMateriel(immo.getTypeMateriel());
        dto.setDateAcquisition(immo.getDateAcquisition());
        dto.setDateCreation(immo.getDateCreation());

        if (immo.getCategorie() != null) {
            dto.setCategorieNom(immo.getCategorie().getNomCategorie());
            dto.setCategorieCode(immo.getCategorie().getCodeComptable());
        }
        if (immo.getAgence() != null) {
            dto.setAgenceNom(immo.getAgence().getNomAgence());
            dto.setAgenceCode(immo.getAgence().getCodeAgence());
        }
        if (immo.getService() != null) {
            dto.setServiceNom(immo.getService().getNomService());
        }
        return dto;
    }
}