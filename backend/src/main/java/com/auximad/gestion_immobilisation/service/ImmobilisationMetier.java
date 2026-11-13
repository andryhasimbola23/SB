package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.ImmobilisationDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.*;
import com.auximad.gestion_immobilisation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImmobilisationMetier {

    private final ImmobilisationRepository immobilisationRepository;
    private final ServiceRepository serviceRepository;
    private final AgenceRepository agenceRepository;
    private final CategorieRepository categorieRepository;
    private final FournisseurRepository fournisseurRepository;
    private final MouvementRepository mouvementRepository;
    private final LigneInventaireRepository ligneInventaireRepository;
    private final DemandeImmobilisationRepository demandeRepository;
    private final QrCodeService qrCodeService;
    private final CodificationService codificationService;
    private final PerimetreService perimetreService;
    private final JournalAuditMetier journalAuditMetier;

    /**
     * @param serviceId périmètre de l'utilisateur courant (null = ADMIN).
     */
    @Transactional(readOnly = true)
    public List<ImmobilisationDto> getAllImmobilisations(Long serviceId) {
        return immobilisationRepository.findAllDansPerimetre(serviceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Lecture unitaire : vérifie que le bien appartient au périmètre de
     * l'utilisateur, sans révéler son existence s'il en est hors.
     */
    @Transactional(readOnly = true)
    public ImmobilisationDto getImmobilisationById(Long id, Utilisateur user) {
        Immobilisation immo = perimetreService.exigerAccessible(
                immobilisationRepository.findById(id), user);
        return toDto(immo);
    }

    /**
     * Lecture par code unique — le point d'entrée du scanner global.
     *
     * Un QR scanné n encode que le code unique (ou « CODE | ETAT : … » pour les
     * étiquettes régénérées) : c'est ce lookup exact qui permet d'identifier un
     * bien en une passe, sans lister tout le parc. Même règle de périmètre que
     * la lecture par id — 404 identique si le bien est hors périmètre, pour ne
     * pas permettre l'énumération des codes.
     */
    @Transactional(readOnly = true)
    public ImmobilisationDto getImmobilisationByCode(String codeUnique, Utilisateur user) {
        Immobilisation immo = perimetreService.exigerAccessible(
                immobilisationRepository.findByCodeUnique(codeUnique), user);
        return toDto(immo);
    }

    /**
     * Contrôle d'accès à une immobilisation (404 si hors périmètre).
     * À appeler avant toute lecture unitaire dérivée (amortissements, VNC,
     * export fiche...), qui ne passe pas par {@link #getImmobilisationById}.
     */
    @Transactional(readOnly = true)
    public void verifierAcces(Long id, Utilisateur user) {
        perimetreService.exigerAccessible(immobilisationRepository.findById(id), user);
    }

    @Transactional
    public ImmobilisationDto createImmobilisation(ImmobilisationDto dto, Utilisateur acteur) {
        com.auximad.gestion_immobilisation.model.Service service = serviceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé"));

        Agence agence = agenceRepository.findById(dto.getAgenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée"));

        Categorie categorie = categorieRepository.findById(dto.getCategorieId())
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));

        Fournisseur fournisseur = null;
        if (dto.getFournisseurId() != null) {
            fournisseur = fournisseurRepository.findById(dto.getFournisseurId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fournisseur non trouvé"));
        }

        String codeUnique = codificationService.genererCodeUnique(agence, service, categorie, dto.getTypeMateriel());

        Immobilisation immo = Immobilisation.builder()
                .codeUnique(codeUnique)
                .designation(dto.getDesignation())
                .dateAcquisition(dto.getDateAcquisition())
                .dateMiseService(dto.getDateMiseService())
                .valeurAchat(dto.getValeurAchat())
                .statut(dto.getStatut() != null ? dto.getStatut() : "ACTIF")
                .etatPhysique(dto.getEtatPhysique())
                .photo(dto.getPhoto())
                .marque(dto.getMarque())
                .tauxAmortissement(dto.getTauxAmortissement())
                .typeMateriel(dto.getTypeMateriel().toUpperCase())
                .description(dto.getDescription())
                .facture(dto.getFacture())
                .service(service)
                .agence(agence)
                .categorie(categorie)
                .fournisseur(fournisseur)
                .build();

        Immobilisation cree = immobilisationRepository.save(immo);

        journalAuditMetier.enregistrer(acteur, JournalAuditMetier.CREATION,
                JournalAuditMetier.ENTITE_IMMOBILISATION, cree.getId(),
                "Bien « " + cree.getDesignation() + " » créé — code " + cree.getCodeUnique());

        return toDto(cree);
    }

    /**
     * Mise à jour d'une immobilisation.
     * ⚠️ IMPORTANT : Le service et l'agence NE PEUVENT PAS être modifiés ici.
     * Ces changements doivent passer par un MOUVEMENT de type TRANSFERT pour garantir la traçabilité.
     */
    @Transactional
    public ImmobilisationDto updateImmobilisation(Long id, ImmobilisationDto dto, Utilisateur acteur) {
        Immobilisation immo = immobilisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        // Mise à jour uniquement des champs "descriptifs"
        immo.setDesignation(dto.getDesignation());
        immo.setDateAcquisition(dto.getDateAcquisition());
        immo.setDateMiseService(dto.getDateMiseService());
        immo.setValeurAchat(dto.getValeurAchat());
        // ⚠️ Le statut (ACTIF / CEDE / REBUTE) n'est PAS modifiable ici : il ne
        // change que par un mouvement de cession ou de rebut, qui en trace
        // l'auteur, la date et le prix. L'ignorer ici empêche qu'une simple
        // édition de fiche fasse disparaître un bien du parc sans laisser de
        // trace comptable.
        immo.setEtatPhysique(dto.getEtatPhysique());
        immo.setPhoto(dto.getPhoto());
        immo.setMarque(dto.getMarque());
        immo.setTauxAmortissement(dto.getTauxAmortissement());
        immo.setTypeMateriel(dto.getTypeMateriel().toUpperCase());
        immo.setDescription(dto.getDescription());
        immo.setFacture(dto.getFacture());

        // Catégorie (autorisée mais peut impacter le code unique si on veut être strict)
        if (dto.getCategorieId() != null) {
            Categorie categorie = categorieRepository.findById(dto.getCategorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée"));
            immo.setCategorie(categorie);
        }

        // Fournisseur (autorisé)
        if (dto.getFournisseurId() != null) {
            Fournisseur fournisseur = fournisseurRepository.findById(dto.getFournisseurId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fournisseur non trouvé"));
            immo.setFournisseur(fournisseur);
        } else {
            immo.setFournisseur(null);
        }

        // ❌ NE PAS toucher à : service, agence, codeUnique, qrCode
        // Ces champs sont gérés exclusivement via les mouvements (TRANSFERT)

        Immobilisation miseAJour = immobilisationRepository.save(immo);

        journalAuditMetier.enregistrer(acteur, JournalAuditMetier.MODIFICATION,
                JournalAuditMetier.ENTITE_IMMOBILISATION, id,
                "Fiche de « " + miseAJour.getDesignation() + " » (" + miseAJour.getCodeUnique() + ") modifiée");

        return toDto(miseAJour);
    }

    /**
     * Suppression contrôlée d'une immobilisation.
     *
     * Un bien qui a déjà vécu (mouvements, constats d'inventaire, demandes
     * livrées) ne se supprime pas : sa trace fait partie de l'historique du
     * parc. Plutôt qu'une erreur 500 « foreign key » en fin de transaction,
     * on renvoie un 409 avec la raison et la marche à suivre (cession/rebut).
     */
    @Transactional
    public void deleteImmobilisation(Long id, Utilisateur acteur) {
        Immobilisation immo = immobilisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        long mouvements = mouvementRepository.countByImmobilisationId(id);
        long constats = ligneInventaireRepository.countByImmobilisationId(id);
        long demandes = demandeRepository.countByImmobilisationId(id);

        if (mouvements > 0 || constats > 0 || demandes > 0) {
            throw new ConflitMetierException(
                    "Impossible de supprimer ce bien : il est lié à " + mouvements + " mouvement(s), "
                    + constats + " constat(s) d'inventaire et " + demandes + " demande(s). "
                    + "Faites-le sortir du parc par un mouvement de cession ou de rebut — "
                    + "la traçabilité du parc doit être conservée.");
        }

        immobilisationRepository.deleteById(id);

        journalAuditMetier.enregistrer(acteur, JournalAuditMetier.SUPPRESSION,
                JournalAuditMetier.ENTITE_IMMOBILISATION, id,
                "Bien « " + immo.getDesignation() + " » (" + immo.getCodeUnique() + ") supprimé — aucune référence en base");
    }

    @Transactional(readOnly = true)
    public List<ImmobilisationDto> searchImmobilisations(String keyword, Long serviceId) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllImmobilisations(serviceId);
        }
        return immobilisationRepository.searchByKeyword(keyword.trim(), serviceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Contenu encodé dans le QR Code, c'est ce qu'un scanner affichera après
     * lecture de l'étiquette :
     *
     *   90/22/IT/2183/IMP_002 | ETAT : BON | STATUT : ACTIF
     *
     * Libellés en ASCII pur, sans accent : un caractère accentué est perdu par
     * la chaîne de génération zxing, et de toute façon la plupart des
     * scanners de téléphone restituent mal les octets non ASCII faute d'ECI.
     * « ETAT » reste parfaitement lisible en français.
     *
     * Le code unique reste en PREMIÈRE position et ne peut contenir « | » : le
     * scanner de l'inventaire n'a donc qu'à découper sur le premier séparateur,
     * ce qui conserve la compatibilité avec les étiquettes déjà imprimées
     * (qui n'encodent que le code unique).
     *
     * L'état reflète la base au moment de la génération : si un inventaire
     * change ensuite `etatPhysique`, il faut régénérer l'image pour que
     * l'étiquette suive.
     */
    private String construireContenuQr(Immobilisation immo) {
        return String.format("%s | ETAT : %s | STATUT : %s",
                immo.getCodeUnique(),
                immo.getEtatPhysique(),
                immo.getStatut());
    }

    /**
     * Génère un QR Code pour une immobilisation.
     */
    @Transactional
    public ImmobilisationDto genererQrCode(Long id) {
        Immobilisation immo = immobilisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        // Le nom de fichier reste dérivé du seul code unique : les URLs et les
        // images déjà présentes sur disque sont réécrites, jamais dédoublonnées.
        String nomFichier = immo.getCodeUnique().replace("/", "_").replace("\\", "_");
        String url = qrCodeService.genererQrCodeAvecLogo(construireContenuQr(immo), nomFichier);

        immo.setQrCode(url);
        return toDto(immobilisationRepository.save(immo));
    }

    private ImmobilisationDto toDto(Immobilisation immo) {
        ImmobilisationDto dto = new ImmobilisationDto();
        dto.setId(immo.getId());
        dto.setCodeUnique(immo.getCodeUnique());
        dto.setDesignation(immo.getDesignation());
        dto.setDateAcquisition(immo.getDateAcquisition());
        dto.setDateMiseService(immo.getDateMiseService());
        dto.setValeurAchat(immo.getValeurAchat());
        dto.setStatut(immo.getStatut());
        dto.setEtatPhysique(immo.getEtatPhysique());
        dto.setPhoto(immo.getPhoto());
        dto.setMarque(immo.getMarque());
        dto.setTauxAmortissement(immo.getTauxAmortissement());
        dto.setTypeMateriel(immo.getTypeMateriel());
        dto.setDescription(immo.getDescription());
        dto.setFacture(immo.getFacture());
        dto.setQrCode(immo.getQrCode());
        dto.setDateCreation(immo.getDateCreation());

        if (immo.getService() != null) {
            dto.setServiceId(immo.getService().getId());
            dto.setServiceNom(immo.getService().getNomService());
        }
        if (immo.getAgence() != null) {
            dto.setAgenceId(immo.getAgence().getId());
            dto.setAgenceNom(immo.getAgence().getNomAgence());
            dto.setAgenceCode(immo.getAgence().getCodeAgence());
        }
        if (immo.getCategorie() != null) {
            dto.setCategorieId(immo.getCategorie().getId());
            dto.setCategorieNom(immo.getCategorie().getNomCategorie());
            dto.setCategorieCode(immo.getCategorie().getCodeComptable());
        }
        if (immo.getFournisseur() != null) {
            dto.setFournisseurId(immo.getFournisseur().getId());
            dto.setFournisseurNom(immo.getFournisseur().getNomFournisseur());
        }
        return dto;
    }
}