package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.MouvementDto;
import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.*;
import com.auximad.gestion_immobilisation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MouvementMetier {

    private final MouvementRepository mouvementRepository;
    private final ImmobilisationRepository immobilisationRepository;
    private final ServiceRepository serviceRepository;
    private final AgenceRepository agenceRepository;
    private final AmortissementService amortissementService;
    private final CodificationService codificationService;
    private final PerimetreService perimetreService;
    private final DemandeImmobilisationRepository demandeRepository;
    private final NotificationMetier notificationMetier;
    private final JournalAuditMetier journalAuditMetier;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public List<MouvementDto> getAllMouvements(Long serviceId) {
        return mouvementRepository.findAllByOrderByDateMouvementDesc(serviceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Mouvements d'une immobilisation.
     * Le filtre est porté par la requête : hors périmètre, la liste est vide —
     * indiscernable d'un bien sans mouvement, donc aucune fuite.
     */
    @Transactional(readOnly = true)
    public List<MouvementDto> getMouvementsByImmobilisation(Long immobilisationId, Long serviceId) {
        return mouvementRepository.findByImmobilisationIdOrderByDateMouvementDesc(immobilisationId, serviceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Lecture unitaire : 404 si le mouvement porte sur un bien hors périmètre. */
    @Transactional(readOnly = true)
    public MouvementDto getMouvementById(Long id, Utilisateur user) {
        Mouvement mvt = mouvementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mouvement non trouvé"));
        if (!perimetreService.peutVoir(mvt.getImmobilisation(), user)) {
            throw new ResourceNotFoundException("Mouvement non trouvé");
        }
        return toDto(mvt);
    }

    /**
     * Crée un mouvement. Selon le type, met à jour l'immobilisation.
     */
    @Transactional
    public MouvementDto createMouvement(MouvementDto dto, Long utilisateurId) {
        Immobilisation immo = immobilisationRepository.findById(dto.getImmobilisationId())
                .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée"));

        String type = dto.getTypeMouvement() != null ? dto.getTypeMouvement().toUpperCase() : "ENTREE";

        Mouvement mouvement = Mouvement.builder()
                .typeMouvement(type)
                .dateMouvement(dto.getDateMouvement() != null ? dto.getDateMouvement() : LocalDateTime.now())
                .ancienneLocalisation(dto.getAncienneLocalisation())
                .nouvelleLocalisation(dto.getNouvelleLocalisation())
                .commentaire(dto.getCommentaire())
                .prixVente(dto.getPrixVente())
                .numeroFacture(dto.getNumeroFacture())
                .immobilisation(immo)
                .build();

        // Récupérer l'utilisateur connecté
        if (utilisateurId != null) {
            Utilisateur u = new Utilisateur();
            u.setId(utilisateurId);
            mouvement.setUtilisateur(u);
        }

        // Lien vers la demande qui a déclenché le mouvement (livraison sur demande)
        if (dto.getDemandeId() != null) {
            mouvement.setDemande(demandeRepository.findById(dto.getDemandeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée")));
        }

        // Traitement selon le type
        switch (type) {
            case "TRANSFERT":
                handleTransfert(immo, dto, mouvement);
                break;
            case "AFFECTATION":
                handleAffectation(immo, mouvement, mouvement.getDemande());
                break;
            case "CESSION":
                handleCession(immo, mouvement);
                break;
            case "REBUT":
                handleRebut(immo, mouvement);
                break;
            case "ENTREE":
            default:
                // Rien à faire de spécial
                break;
        }

        // Sauvegarder l'immobilisation mise à jour
        immobilisationRepository.save(immo);

        // Sorties définitives du parc : les autres administrateurs sont prévenus
        // (l'acteur lui-même ne l'est pas — garde-fou du notifier).
        if ("CESSION".equals(type)) {
            notificationMetier.notifierAdmins(
                    NotificationMetier.BIEN_CEDER,
                    "Bien cédé : " + immo.getDesignation(),
                    immo.getCodeUnique()
                            + (mouvement.getPrixVente() != null ? " — prix de vente " + mouvement.getPrixVente() : ""),
                    "/mouvements",
                    utilisateurId);
        } else if ("REBUT".equals(type)) {
            notificationMetier.notifierAdmins(
                    NotificationMetier.BIEN_REBUTE,
                    "Bien rebuté : " + immo.getDesignation(),
                    immo.getCodeUnique() + " — retiré définitivement du parc.",
                    "/mouvements",
                    utilisateurId);
        }

        Mouvement enregistre = mouvementRepository.save(mouvement);

        // Journal d'audit : chaque mouvement est une trace du parc — le type
        // de mouvement EST l'action auditée (mêmes noms de constantes).
        journalAuditMetier.enregistrer(
                utilisateurId != null ? utilisateurRepository.findById(utilisateurId).orElse(null) : null,
                type,
                JournalAuditMetier.ENTITE_MOUVEMENT,
                enregistre.getId(),
                immo.getDesignation() + " (" + immo.getCodeUnique() + ")");

        return toDto(enregistre);
    }

    /**
     * Transfert : change le service et/ou l'agence, régénère le code unique.
     */
    private void handleTransfert(Immobilisation immo, MouvementDto dto, Mouvement mouvement) {
        // Sauvegarder l'ancienne localisation AVANT modification
        String ancienService = immo.getService() != null ? immo.getService().getNomService() : "";
        String ancienneAgence = immo.getAgence() != null ? immo.getAgence().getNomAgence() : "";
        mouvement.setAncienneLocalisation(ancienneAgence + " - " + ancienService);

        // Appliquer le nouveau service
        if (dto.getNouveauServiceId() != null) {
            com.auximad.gestion_immobilisation.model.Service nouveauService =
                    serviceRepository.findById(dto.getNouveauServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service non trouvé"));
            immo.setService(nouveauService);
        }

        // Appliquer la nouvelle agence
        if (dto.getNouvelleAgenceId() != null) {
            Agence nouvelleAgence = agenceRepository.findById(dto.getNouvelleAgenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agence non trouvée"));
            immo.setAgence(nouvelleAgence);
        }

        // Construire la nouvelle localisation
        String nouveauService = immo.getService() != null ? immo.getService().getNomService() : "";
        String nouvelleAgence = immo.getAgence() != null ? immo.getAgence().getNomAgence() : "";
        mouvement.setNouvelleLocalisation(nouvelleAgence + " - " + nouveauService);

        // ✅ RÉGÉNÉRER LE CODE UNIQUE
        String ancienCode = immo.getCodeUnique();
        String nouveauCode = codificationService.genererCodeUnique(
                immo.getAgence(),
                immo.getService(),
                immo.getCategorie(),
                immo.getTypeMateriel()
        );
        immo.setCodeUnique(nouveauCode);

        // ✅ INVALIDER L'ANCIEN QR CODE
        immo.setQrCode(null);

        // Ajouter au commentaire la mention du changement de code
        String commentaireExistant = mouvement.getCommentaire() != null ? mouvement.getCommentaire() : "";
        String mention = "\n[Code unique changé : " + ancienCode + " → " + nouveauCode + "]";
        mouvement.setCommentaire(commentaireExistant + mention);
    }

    /**
     * Affectation : le bien est remis à quelqu'un SANS changer de service.
     *
     * Rien n'est modifié sur l'immobilisation — ni service, ni code unique, ni QR.
     * Le bien ne bouge pas physiquement : seule la remise est tracée. Réémettre
     * un code et invalider un QR déjà imprimés pour un déplacement nul ne serait * que du bruit.
     *
     * Contrairement au transfert, l'ancienne et la nouvelle localisation sont donc * identiques : c'est le signal lisible que le bien n'a pas bougé de service.
     */
    private void handleAffectation(Immobilisation immo, Mouvement mouvement, DemandeImmobilisation demande) {
        String agence = immo.getAgence() != null ? immo.getAgence().getNomAgence() : "";
        String service = immo.getService() != null ? immo.getService().getNomService() : "";
        String lieu = agence + " - " + service;

        mouvement.setAncienneLocalisation(lieu);
        mouvement.setNouvelleLocalisation(lieu);

        String destinataire = null;
        if (demande != null && demande.getDemandeur() != null) {
            destinataire = (demande.getDemandeur().getPrenom() + " " + demande.getDemandeur().getNom()).trim();
        }
        String mention = "\n[Affecté à " + (destinataire != null && !destinataire.isEmpty() ? destinataire : "—") + "]";
        mouvement.setCommentaire((mouvement.getCommentaire() != null ? mouvement.getCommentaire() : "") + mention);
    }

    /**
     * Cession : calcule la VNC, la plus/moins-value et met le statut à CEDE.
     */
    private void handleCession(Immobilisation immo, Mouvement mouvement) {
        BigDecimal vnc = amortissementService.calculerVncActuelle(immo.getId());
        mouvement.setValeurVnc(vnc);

        if (mouvement.getPrixVente() != null) {
            BigDecimal pmv = mouvement.getPrixVente().subtract(vnc);
            mouvement.setPlusMoinsValue(pmv);
        }

        immo.setStatut("CEDE");
    }

    /**
     * Rebut : calcule la VNC et met le statut à REBUTE.
     */
    private void handleRebut(Immobilisation immo, Mouvement mouvement) {
        BigDecimal vnc = amortissementService.calculerVncActuelle(immo.getId());
        mouvement.setValeurVnc(vnc);
        immo.setStatut("REBUTE");
    }

    /**
     * Les mouvements sont immuables.
     *
     * Un mouvement est une écriture comptable : il trace qui a fait quoi, quand,
     * et avec quelles valeurs (VNC, plus/moins-value). L'effacer reviendrait à
     * falsifier l'historique du parc. L'interface n'expose d'ailleurs aucune
     * action de suppression — cette règle protège l'API contre un appel direct.
     * En cas d'erreur de saisie, la correction passe par un nouveau mouvement.
     */
    @Transactional
    public void deleteMouvement(Long id) {
        if (!mouvementRepository.existsById(id)) {
            throw new ResourceNotFoundException("Mouvement non trouvé");
        }
        throw new ConflitMetierException(
                "Les mouvements sont immuables : ils constituent la trace comptable du parc "
                + "et ne peuvent pas être supprimés. En cas d'erreur de saisie, "
                + "enregistrez un mouvement de correction.");
    }

    private MouvementDto toDto(Mouvement mvt) {
        MouvementDto dto = new MouvementDto();
        dto.setId(mvt.getId());
        dto.setTypeMouvement(mvt.getTypeMouvement());
        dto.setDateMouvement(mvt.getDateMouvement());
        dto.setAncienneLocalisation(mvt.getAncienneLocalisation());
        dto.setNouvelleLocalisation(mvt.getNouvelleLocalisation());
        dto.setValeurVnc(mvt.getValeurVnc());
        dto.setCommentaire(mvt.getCommentaire());
        dto.setPrixVente(mvt.getPrixVente());
        dto.setNumeroFacture(mvt.getNumeroFacture());
        dto.setPlusMoinsValue(mvt.getPlusMoinsValue());
        dto.setDateCreation(mvt.getDateCreation());

        if (mvt.getImmobilisation() != null) {
            dto.setImmobilisationId(mvt.getImmobilisation().getId());
            dto.setImmobilisationCode(mvt.getImmobilisation().getCodeUnique());
            dto.setImmobilisationDesignation(mvt.getImmobilisation().getDesignation());
        }
        if (mvt.getUtilisateur() != null) {
            dto.setUtilisateurId(mvt.getUtilisateur().getId());
            dto.setUtilisateurNom(
                    mvt.getUtilisateur().getPrenom() + " " + mvt.getUtilisateur().getNom()
            );
        }

        if (mvt.getDemande() != null) {
            dto.setDemandeId(mvt.getDemande().getId());
            dto.setDemandeReference(DemandeMetier.reference(mvt.getDemande()));
        }

        return dto;
    }
}