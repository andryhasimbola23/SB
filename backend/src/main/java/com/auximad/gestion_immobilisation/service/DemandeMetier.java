package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.DemandeDecisionDto;
import com.auximad.gestion_immobilisation.dto.DemandeDto;
import com.auximad.gestion_immobilisation.dto.DemandeStatsDto;
import com.auximad.gestion_immobilisation.dto.ImmobilisationCandidatDto;
import com.auximad.gestion_immobilisation.dto.MouvementDto;
import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Categorie;
import com.auximad.gestion_immobilisation.model.DemandeImmobilisation;
import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.model.Mouvement;
import com.auximad.gestion_immobilisation.model.PrioriteDemande;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.StatutDemande;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.CategorieRepository;
import com.auximad.gestion_immobilisation.repository.DemandeImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.ImmobilisationRepository;
import com.auximad.gestion_immobilisation.repository.MouvementRepository;
import com.auximad.gestion_immobilisation.repository.ServiceRepository;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Métier des demandes d'immobilisation (employé → administrateur).
 *
 * Périmètre : l'ADMIN consulte toutes les demandes ; l'EMPLOYE uniquement les
 * siennes. C'est une différence assumée avec le module immobilisations, où le
 * filtre portait sur le service — ici la demande exprime le besoin d'une
 * personne, et les besoins des collègues ne sont pas son affaire.
 */
@Service
@RequiredArgsConstructor
public class DemandeMetier {

    private final DemandeImmobilisationRepository demandeRepository;
    private final CategorieRepository categorieRepository;
    private final ImmobilisationRepository immobilisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ServiceRepository serviceRepository;
    private final MouvementRepository mouvementRepository;
    private final MouvementMetier mouvementMetier;
    private final PerimetreService perimetreService;
    private final NotificationMetier notificationMetier;
    private final JournalAuditMetier journalAuditMetier;
    // Temps réel best effort : null dans les tests unitaires sans contexte Spring.
    private final RealtimeMetier realtimeMetier;

    // ============================================
    // PÉRIMÈTRE
    // ============================================

    private boolean estAdmin(Utilisateur user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private boolean toutesPour(Utilisateur user) {
        return estAdmin(user);
    }

    /**
     * Charge une demande en appliquant le périmètre.
     *
     * Les deux refus (demande inexistante, demande d'autrui) produisent exactement
     * la même exception et le même message : un 404 indifférencié ne permet pas de
     * sonder l'existence des demandes des autres en parcourant les ids.
     */
    private DemandeImmobilisation chargerDansPerimetre(Long id, Utilisateur user) {
        if (user == null || user.getId() == null || id == null) {
            throw new ResourceNotFoundException("Demande non trouvée");
        }
        if (estAdmin(user)) {
            return demandeRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));
        }
        return demandeRepository.findByIdAndDemandeurId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));
    }

    // ============================================
    // LECTURE
    // ============================================

    /**
     * Liste des demandes, restreinte au périmètre de l'appelant.
     *
     * @param keyword  recherche textuelle facultative
     * @param statut   filtre facultatif : EN_ATTENTE, APPROUVEE, REJETTEE, ANNULEE
     */
    @Transactional(readOnly = true)
    public List<DemandeDto> getAll(Utilisateur user, String keyword, String statut) {
        boolean toutes = toutesPour(user) && user != null && user.getId() != null;
        Long demandeurId = toutes ? null : (user != null ? user.getId() : null);

        // Fail-closed : sans utilisateur identifié, aucun périmètre n'est accordé.
        if (demandeurId == null && !toutes) {
            return List.of();
        }

        List<DemandeImmobilisation> demandes =
                (keyword == null || keyword.trim().isEmpty())
                        ? demandeRepository.findAllDansPerimetre(toutes, demandeurId)
                        : demandeRepository.searchDansPerimetre(keyword.trim(), toutes, demandeurId);

        if (statut != null && !statut.trim().isEmpty()) {
            StatutDemande attendu = parseStatut(statut);
            demandes = demandes.stream()
                    .filter(d -> d.getStatut() == attendu)
                    .collect(Collectors.toList());
        }

        Map<Long, Mouvement> livraisons = livraisonsDe(demandes);
        return demandes.stream()
                .map(d -> toDto(d, livraisons.get(d.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandeDto getById(Long id, Utilisateur user) {
        DemandeImmobilisation demande = chargerDansPerimetre(id, user);
        return toDto(demande, livraisonDe(demande.getId()));
    }

    /**
     * Livraisons d'un lot de demandes, en une seule requête.
     *
     * Le lien demande -> mouvement est porté par le mouvement ; s'il était lu
     * ligne à ligne depuis `toDto`, une liste de 20 demandes déclencherait 20
     * requêtes. Ici, un aller-retour, et les demandes sans livraison occupent
     * simplement une valeur absente de la carte.
     */
    private Map<Long, Mouvement> livraisonsDe(List<DemandeImmobilisation> demandes) {
        if (demandes.isEmpty()) return Map.of();
        List<Long> ids = demandes.stream().map(DemandeImmobilisation::getId).collect(Collectors.toList());
        return mouvementRepository.findByDemandeIdIn(ids).stream()
                .collect(Collectors.toMap(m -> m.getDemande().getId(), Function.identity(), (a, b) -> a));
    }

    private Mouvement livraisonDe(Long demandeId) {
        return mouvementRepository.findByDemandeIdIn(List.of(demandeId)).stream()
                .findFirst()
                .orElse(null);
    }

    /** Compteurs adaptés au périmètre : totaux entreprise ou totaux personnels. */
    @Transactional(readOnly = true)
    public DemandeStatsDto getStats(Utilisateur user) {
        if (estAdmin(user)) {
            return DemandeStatsDto.builder()
                    .total(demandeRepository.count())
                    .enAttente(demandeRepository.countByStatut(StatutDemande.EN_ATTENTE))
                    .approuvees(demandeRepository.countByStatut(StatutDemande.APPROUVEE))
                    .rejettees(demandeRepository.countByStatut(StatutDemande.REJETTEE))
                    .annulees(demandeRepository.countByStatut(StatutDemande.ANNULEE))
                    .build();
        }

        if (user == null || user.getId() == null) {
            return DemandeStatsDto.builder().build(); // fail-closed : tout à zéro
        }

        Long demandeurId = user.getId();
        long enAttente = demandeRepository.countByDemandeurIdAndStatut(demandeurId, StatutDemande.EN_ATTENTE);
        long approuvees = demandeRepository.countByDemandeurIdAndStatut(demandeurId, StatutDemande.APPROUVEE);
        long rejettees = demandeRepository.countByDemandeurIdAndStatut(demandeurId, StatutDemande.REJETTEE);
        long annulees = demandeRepository.countByDemandeurIdAndStatut(demandeurId, StatutDemande.ANNULEE);

        return DemandeStatsDto.builder()
                .total(demandeRepository.countByDemandeurId(demandeurId))
                .enAttente(enAttente)
                .approuvees(approuvees)
                .rejettees(rejettees)
                .annulees(annulees)
                .build();
    }

    /**
     * Biens que l'administrateur peut remettre sur une demande.
     *
     * Porte le périmètre de l'appelant — nul pour un admin — comme toutes les
     * lectures de cette classe : la règle est déjà posée si l'endpoint était un
     * jour élargi. Les autres garde-fous (bien actif, non hors service,
     * codifiable) vivent dans la requête elle-même, pour que le sélecteur ne
     * propose jamais un bien que le serveur refusera ensuite.
     */
    @Transactional(readOnly = true)
    public List<ImmobilisationCandidatDto> getCandidats(Long categorieId, String q, Utilisateur user) {
        Long serviceId = perimetreService.serviceIdDe(user);
        String recherche = q == null ? "" : q.trim();

        return immobilisationRepository.rechercherCandidats(serviceId, categorieId, recherche)
                .stream()
                .map(this::toCandidatDto)
                .collect(Collectors.toList());
    }

    private ImmobilisationCandidatDto toCandidatDto(Immobilisation i) {
        ImmobilisationCandidatDto dto = new ImmobilisationCandidatDto();
        dto.setId(i.getId());
        dto.setCodeUnique(i.getCodeUnique());
        dto.setDesignation(i.getDesignation());
        dto.setMarque(i.getMarque());
        dto.setEtatPhysique(i.getEtatPhysique());

        if (i.getService() != null) {
            dto.setServiceId(i.getService().getId());
            dto.setServiceNom(i.getService().getNomService());
        }
        if (i.getCategorie() != null) {
            dto.setCategorieId(i.getCategorie().getId());
            dto.setCategorieNom(i.getCategorie().getNomCategorie());
        }
        if (i.getAgence() != null) {
            dto.setAgenceNom(i.getAgence().getNomAgence());
        }
        return dto;
    }

    // ============================================
    // ÉCRITURE
    // ============================================

    /**
     * Dépôt d'une demande par l'utilisateur connecté.
     *
     * Trois points ne sont jamais repris du client :
     * le demandeur, le service (figé à l'instant du dépôt) et le statut.
     */
    @Transactional
    public DemandeDto create(DemandeDto dto, Utilisateur user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Utilisateur authentifié requis");
        }

        // Rechargement en base : le principal passé par le filtre JWT est détaché,
        // il doit être géré pour pouvoir être rattaché à la demande persistée.
        Utilisateur demandeur = utilisateurRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur authentifié introuvable"));

        Categorie categorie = null;
        if (dto.getCategorieId() != null) {
            categorie = categorieRepository.findById(dto.getCategorieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie non trouvée avec l'ID : " + dto.getCategorieId()));
        }

        DemandeImmobilisation demande = DemandeImmobilisation.builder()
                .designation(dto.getDesignation().trim())
                .motif(dto.getMotif().trim())
                .quantite(dto.getQuantite() == null || dto.getQuantite() < 1 ? 1 : dto.getQuantite())
                .priorite(parsePriorite(dto.getPriorite()))
                .besoinLe(dto.getBesoinLe())
                .statut(StatutDemande.EN_ATTENTE)
                .demandeur(demandeur)
                .service(demandeur.getService()) // figé : ne doit pas suivre l'employé
                .categorie(categorie)
                .build();

        DemandeImmobilisation enregistree = demandeRepository.save(demande);

        // In-app : l'ADMIN doit savoir qu'une demande attend une décision.
        notificationMetier.notifierAdmins(
                NotificationMetier.NOUVELLE_DEMANDE,
                "Nouvelle demande d'immobilisation",
                nomComplet(demandeur) + " — « " + enregistree.getDesignation() + " »"
                        + (enregistree.getQuantite() > 1 ? " x" + enregistree.getQuantite() : ""),
                "/demandes",
                demandeur.getId());

        // Temps réel : le demandeur voit la demande apparaître sur chacune de ses
        // sessions, les admins rechargent leur liste et leur badge sans F5.
        publierDemandes(demandeur.getId(), RealtimeMetier.EVT_NOUVELLE_DEMANDE, enregistree.getId());
        publierDemandesAuxAdmins(RealtimeMetier.EVT_NOUVELLE_DEMANDE, enregistree.getId());

        return toDto(enregistree);
    }

    /**
     * Décision d'un administrateur : approbation ou refus.
     *
     * Réservée aux demandes encore en attente, et un refus sans motif est refusé :
     * un « non » muet renvoie l'employé en arrière sans qu'il sache quoi corriger.
     *
     * Une approbation qui remet un bien ne se contente pas de le pointer : le bien
     * change réellement de service et le geste apparaît dans l'historique des
     * mouvements (voir {@link #livrerBien}).
     */
    @Transactional
    public DemandeDto decider(Long id, DemandeDecisionDto dto, Utilisateur admin) {
        if (!estAdmin(admin)) {
            throw new IllegalArgumentException("Décision réservée à un administrateur");
        }

        DemandeImmobilisation demande = demandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée avec l'ID : " + id));

        if (!demande.getStatut().estEnAttente()) {
            throw new IllegalArgumentException(
                    "La demande " + reference(demande) + " porte le statut « "
                            + demande.getStatut().name() + " » et ne peut plus être traitée.");
        }

        String decision = dto.getDecision() == null ? "" : dto.getDecision().trim().toUpperCase(Locale.ROOT);
        StatutDemande nouveau;
        if ("APPROUVEE".equals(decision) || "APPROUVE".equals(decision)) {
            nouveau = StatutDemande.APPROUVEE;
        } else if ("REJETTEE".equals(decision) || "REJETE".equals(decision) || "REJETTE".equals(decision)) {
            nouveau = StatutDemande.REJETTEE;
        } else {
            throw new IllegalArgumentException("Décision invalide : « " + dto.getDecision() + " » (attendu APPROUVEE ou REJETTEE)");
        }

        String commentaire = dto.getCommentaire() == null ? null : dto.getCommentaire().trim();
        if (nouveau == StatutDemande.REJETTEE && (commentaire == null || commentaire.isEmpty())) {
            throw new IllegalArgumentException("Le motif du refus est obligatoire : l'employé doit savoir pourquoi.");
        }

        demande.setStatut(nouveau);
        demande.setCommentaireTraitement(commentaire == null || commentaire.isEmpty() ? null : commentaire);
        demande.setDateTraitement(LocalDateTime.now());
        demande.setTraiteePar(utilisateurRepository.findById(admin.getId())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur authentifié introuvable")));

        // Livraison d'un bien — uniquement en cas d'approbation, et seulement si
        // un bien est effectivement remis : une approbation « on achètera plus tard »
        // reste donc possible sans mouvement.
        Mouvement livraison = null;
        if (nouveau == StatutDemande.APPROUVEE && dto.getImmobilisationId() != null) {
            livraison = livrerBien(demande, dto, admin);
        }

        DemandeDto resultat = toDto(demandeRepository.save(demande), livraison);

        // Journal d'audit : la décision engage le parc (et parfois un bien remis).
        journalAuditMetier.enregistrer(demande.getTraiteePar(),
                JournalAuditMetier.DECISION_DEMANDE, JournalAuditMetier.ENTITE_DEMANDE, demande.getId(),
                reference(demande) + " " + (nouveau == StatutDemande.APPROUVEE ? "approuvée" : "rejetée")
                        + (demande.getImmobilisation() != null
                                ? " — bien remis " + demande.getImmobilisation().getCodeUnique()
                                : ""));

        // In-app : le demandeur suit sa demande sans recharger la page.
        if (nouveau == StatutDemande.APPROUVEE) {
            boolean avecBien = demande.getImmobilisation() != null;
            notificationMetier.notifier(
                    demande.getDemandeur().getId(),
                    NotificationMetier.DEMANDE_APPROUVEE,
                    "Demande " + reference(demande) + " approuvée",
                    avecBien
                            ? "Le bien " + demande.getImmobilisation().getCodeUnique()
                                    + " vous est remis (" + demande.getImmobilisation().getDesignation() + ")."
                            : "Votre demande est approuvée.",
                    "/demandes",
                    admin.getId());
        } else if (nouveau == StatutDemande.REJETTEE) {
            notificationMetier.notifier(
                    demande.getDemandeur().getId(),
                    NotificationMetier.DEMANDE_REJETEE,
                    "Demande " + reference(demande) + " rejetée",
                    "Motif : " + demande.getCommentaireTraitement(),
                    "/demandes",
                    admin.getId());
        }

        // Temps réel : le demandeur voit le statut basculer (Acceptée/Rejetée)
        // sur tous ses navigateurs ; les admins voient la demande quitter la
        // file d'attente. Seul le demandeur reçoit la notification ci-dessus.
        publierDemandes(demande.getDemandeur().getId(), RealtimeMetier.EVT_DEMANDE_DECIDEE, demande.getId());
        publierDemandesAuxAdmins(RealtimeMetier.EVT_DEMANDE_DECIDEE, demande.getId());

        return resultat;
    }

    /**
     * Livraison effective d'un bien lors de l'approbation.
     *
     * Le service de destination vient de la demande — figé au dépôt — sauf pour
     * un employé non affecté (service null), où l'administrateur doit le choisir
     * explicitement : une livraison a besoin d'une adresse.
     *
     * Deux cas, tous deux tracés :
     *  - le bien appartient déjà au service de destination -> AFFECTATION :
     *    il ne bouge pas, seule la remise est historisée (réémettre un code et
     *    invalider un QR imprimés pour un déplacement nul serait du bruit) ;
     *  - sinon -> TRANSFERT : service et agence suivent, code unique réémis,
     *    QR invalidé.
     *
     * La mutation passe par {@link MouvementMetier}, seul chemin autorisé à
     * changer le service d'une immobilisation — règle posée dans
     * `ImmobilisationMetier`. Toute l'opération partage la transaction de
     * `decider()` : si le mouvement échoue, l'approbation est annulée avec lui et
     * la demande reste en attente, jamais à moitié décidée.
     */
    private Mouvement livrerBien(DemandeImmobilisation demande, DemandeDecisionDto dto, Utilisateur admin) {
        Immobilisation immo = immobilisationRepository.findById(dto.getImmobilisationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Immobilisation non trouvée avec l'ID : " + dto.getImmobilisationId()));

        com.auximad.gestion_immobilisation.model.Service destination = destinationDe(demande, dto);

        if (demandeRepository.existsByImmobilisationIdAndStatut(immo.getId(), StatutDemande.APPROUVEE)) {
            throw new IllegalArgumentException("Le bien " + immo.getCodeUnique()
                    + " a déjà été remis pour une autre demande approuvée.");
        }
        if (!"ACTIF".equals(immo.getStatut())) {
            throw new IllegalArgumentException("Le bien " + immo.getCodeUnique() + " porte le statut « "
                    + immo.getStatut() + " » et ne peut pas être remis.");
        }
        if ("HORS_SERVICE".equals(immo.getEtatPhysique())) {
            throw new IllegalArgumentException("Le bien " + immo.getCodeUnique()
                    + " est hors service et ne peut pas être remis.");
        }
        // `genererCodeUnique` déréférence agence, service et catégorie sans garde :
        // mieux vaut un 400 explicite qu'un NPE qui ressort en 500.
        if (immo.getCategorie() == null) {
            throw new IllegalArgumentException("Le bien " + immo.getCodeUnique()
                    + " n'a pas de catégorie : codification impossible.");
        }
        if (immo.getAgence() == null) {
            throw new IllegalArgumentException("Le bien " + immo.getCodeUnique()
                    + " n'est rattaché à aucune agence : codification impossible.");
        }

        boolean memeService = immo.getService() != null
                && immo.getService().getId().equals(destination.getId());

        MouvementDto mv = new MouvementDto();
        mv.setTypeMouvement(memeService ? "AFFECTATION" : "TRANSFERT");
        mv.setImmobilisationId(immo.getId());
        mv.setNouveauServiceId(destination.getId());
        // L'agence suit le service de destination quand celui-ci en porte une ;
        // sinon la précédente est conservée par le transfert.
        mv.setNouvelleAgenceId(destination.getAgence() != null ? destination.getAgence().getId() : null);
        mv.setDemandeId(demande.getId());
        mv.setCommentaire("Livraison de la demande " + reference(demande) + " — " + demande.getMotif());

        MouvementDto cree = mouvementMetier.createMouvement(mv, admin.getId());

        // Le code unique vient d'être réémis pour un transfert : `immo` est
        // l'entité gérée que le mouvement vient de modifier, donc la demande pointe
        // déjà vers le nouveau code à la lecture.
        demande.setImmobilisation(immo);

        return mouvementRepository.findById(cree.getId()).orElse(null);
    }

    /**
     * Service de destination du bien remis.
     *
     * Priorité au choix de l'administrateur ; sinon celui de la demande. Une
     * demande d'employé non affecté n'a aucun service : sans destination choisie,
     * la livraison n'aurait pas d'adresse — on l'exige plutôt que d'accepter un
     * mouvement muet.
     */
    private com.auximad.gestion_immobilisation.model.Service destinationDe(
            DemandeImmobilisation demande, DemandeDecisionDto dto) {
        if (dto.getDestinationServiceId() != null) {
            return serviceRepository.findById(dto.getDestinationServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Service de destination introuvable avec l'ID : " + dto.getDestinationServiceId()));
        }
        if (demande.getService() != null) {
            return demande.getService();
        }
        throw new IllegalArgumentException(
                "La demande " + reference(demande)
                        + " n'est rattachée à aucun service : indiquez le service de destination du bien.");
    }

    /**
     * Retrait de sa propre demande, tant qu'elle n'a pas été traitée.
     *
     * Seul l'auteur peut retirer — y compris un administrateur : le retrait est
     * un geste de demandeur, et un admin dispose de la décision
     * (approbation/refus) pour toute demande. Comme ailleurs, une demande
     * d'autrui donne un 404 identique à celle d'une demande inexistante.
     *
     * Une fois décidée, la demande est historique : on en ouvre une nouvelle
     * plutôt que d'effacer la trace d'une décision déjà prise.
     */
    @Transactional
    public DemandeDto annuler(Long id, Utilisateur user) {
        if (user == null || user.getId() == null || id == null) {
            throw new ResourceNotFoundException("Demande non trouvée");
        }
        DemandeImmobilisation demande = demandeRepository
                .findByIdAndDemandeurId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));

        if (!demande.getStatut().estEnAttente()) {
            throw new IllegalArgumentException(
                    "La demande " + reference(demande) + " est déjà « " + demande.getStatut().name()
                            + " » et ne peut plus être annulée.");
        }

        demande.setStatut(StatutDemande.ANNULEE);
        demande.setDateTraitement(LocalDateTime.now());
        // Seul l'auteur retire : le demandeur est déjà chargé et géré.
        demande.setTraiteePar(demande.getDemandeur());

        DemandeImmobilisation annulee = demandeRepository.save(demande);

        // Temps réel : l'auteur et les admins rechargent leur liste — la demande
        // quitte la file d'attente côté administrateur sans actualisation.
        publierDemandes(user.getId(), RealtimeMetier.EVT_DEMANDE_ANNULEE, annulee.getId());
        publierDemandesAuxAdmins(RealtimeMetier.EVT_DEMANDE_ANNULEE, annulee.getId());

        return toDto(annulee);
    }

    // ============================================
    // TEMPS RÉEL
    // ============================================

    /**
     * Pousse un événement « rechargez les demandes » vers un utilisateur.
     *
     * Best effort, jamais bloquant : le temps réel complète l'opération, il ne
     * la conditionne pas (voir {@link RealtimeMetier}).
     */
    private void publierDemandes(Long destinataireId, String evenement, Long demandeId) {
        if (realtimeMetier != null) {
            realtimeMetier.publierDemandes(destinataireId, evenement, demandeId);
        }
    }

    /** Même événement, vers tous les administrateurs (liste et badge synchronisés). */
    private void publierDemandesAuxAdmins(String evenement, Long demandeId) {
        if (realtimeMetier != null) {
            realtimeMetier.publierDemandesAuxAdmins(evenement, demandeId);
        }
    }

    // ============================================
    // CONVERSIONS
    // ============================================

    /**
     * Référence de suivi, dérivée sans colonne :
     * la date d'année et l'id sont immuables, donc la référence l'est aussi, et
     * unique de fait — aucune séquence à générer, aucun verrou à poser.
     *
     * Statique : `MouvementMetier` en a besoin pour tracer d'où vient un mouvement,
     * et une dépendance d'objet des deux côtés formerait un cycle.
     */
    public static String reference(DemandeImmobilisation d) {
        if (d == null || d.getId() == null || d.getDateCreation() == null) return null;
        return String.format("DEM-%d-%04d", d.getDateCreation().getYear(), d.getId());
    }

    private String nomComplet(Utilisateur u) {
        if (u == null) return null;
        return (u.getPrenom() + " " + u.getNom()).trim();
    }

    private PrioriteDemande parsePriorite(String valeur) {
        if (valeur == null || valeur.trim().isEmpty()) return PrioriteDemande.NORMALE;
        try {
            return PrioriteDemande.valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Priorité invalide : « " + valeur + " » (attendu NORMALE ou URGENTE)");
        }
    }

    private StatutDemande parseStatut(String valeur) {
        try {
            return StatutDemande.valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Filtre de statut inconnu : « " + valeur + " »");
        }
    }

    public DemandeDto toDto(DemandeImmobilisation d) {
        return toDto(d, null);
    }

    /**
     * @param livraison mouvement créé par la livraison, ou {@code null} pour une
     *                  demande qui n'a rien remis. Passé explicitement plutôt que
     *                  relu en base : `decider` l'a sous la main, et une liste
     *                  complète les obtient par lot.
     */
    public DemandeDto toDto(DemandeImmobilisation d, Mouvement livraison) {
        DemandeDto dto = new DemandeDto();
        dto.setId(d.getId());
        dto.setReference(reference(d));
        dto.setDesignation(d.getDesignation());
        dto.setMotif(d.getMotif());
        dto.setQuantite(d.getQuantite());
        dto.setPriorite(d.getPriorite() != null ? d.getPriorite().name() : PrioriteDemande.NORMALE.name());
        dto.setBesoinLe(d.getBesoinLe());
        dto.setStatut(d.getStatut() != null ? d.getStatut().name() : StatutDemande.EN_ATTENTE.name());
        dto.setDateCreation(d.getDateCreation());

        if (d.getCategorie() != null) {
            dto.setCategorieId(d.getCategorie().getId());
            dto.setCategorieNom(d.getCategorie().getNomCategorie());
        }

        if (d.getDemandeur() != null) {
            dto.setDemandeurId(d.getDemandeur().getId());
            dto.setDemandeurNom(nomComplet(d.getDemandeur()));
        }

        if (d.getService() != null) {
            dto.setServiceId(d.getService().getId());
            dto.setServiceNom(d.getService().getNomService());
            dto.setServiceAffiche(d.getService().getNomService());
        } else {
            dto.setServiceAffiche("Non affecté");
        }

        if (d.getTraiteePar() != null) {
            dto.setTraiteeParId(d.getTraiteePar().getId());
            dto.setTraiteeParNom(nomComplet(d.getTraiteePar()));
        }

        dto.setCommentaireTraitement(d.getCommentaireTraitement());
        dto.setDateTraitement(d.getDateTraitement());

        if (d.getImmobilisation() != null) {
            dto.setImmobilisationId(d.getImmobilisation().getId());
            dto.setImmobilisationCode(d.getImmobilisation().getCodeUnique());
            dto.setImmobilisationDesignation(d.getImmobilisation().getDesignation());
        }

        if (livraison != null) {
            dto.setMouvementId(livraison.getId());
            dto.setMouvementType(livraison.getTypeMouvement());
        }

        return dto;
    }
}
