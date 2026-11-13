package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.DemandeImmobilisation;
import com.auximad.gestion_immobilisation.model.StatutDemande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Périmètre des demandes : le filtre porte sur le demandeur, PAS sur le service.
 *
 * Contrairement aux immobilisations (bien de patrimoine, lu par service), une
 * demande exprime le besoin d'une personne : un employé ne voit que les siennes,
 * jamais celles de ses collègues.
 *
 * {@code toutes} vaut true pour un ADMIN (périmètre illimité), false sinon —
 * auquel cas l'id du demandeur prend le relais.
 */
@Repository
public interface DemandeImmobilisationRepository extends JpaRepository<DemandeImmobilisation, Long> {

    /**
     * Liste paginable ordonnée, restreinte au périmètre de l'appelant.
     * Aucune ligne n'est rangée par statut ici : le tri par statut se fait en
     * service, pour éviter une clause dynamique sur une colonne énumérée.
     */
    @Query("SELECT d FROM DemandeImmobilisation d " +
           "WHERE (:toutes = true OR d.demandeur.id = :demandeurId) " +
           "ORDER BY d.dateCreation DESC")
    List<DemandeImmobilisation> findAllDansPerimetre(@Param("toutes") boolean toutes,
                                                     @Param("demandeurId") Long demandeurId);

    /**
     * Recherche textuelle, même périmètre.
     * Les conditions portent entre parenthèses : sans elles, l'OR échapperait au
     * filtre de périmètre et une recherche d'admin découvrirait les demandes d'autrui.
     */
    @Query("SELECT d FROM DemandeImmobilisation d WHERE " +
           "(LOWER(d.designation) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(d.motif) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:toutes = true OR d.demandeur.id = :demandeurId) " +
           "ORDER BY d.dateCreation DESC")
    List<DemandeImmobilisation> searchDansPerimetre(@Param("keyword") String keyword,
                                                    @Param("toutes") boolean toutes,
                                                    @Param("demandeurId") Long demandeurId);

    /**
     * Accès unitaire en périmètre restreint : renvoie vide si la demande n'existe
     * pas <em>ou</em> si elle appartient à quelqu'un d'autre — le même effetsur les
     * deux cas, de sorte que le contrôleur renvoie un 404 identique dans les deux
     * situations et ne révèle rien par énumération.
     */
    Optional<DemandeImmobilisation> findByIdAndDemandeurId(Long id, Long demandeurId);

    /** Compteur global (ADMIN). */
    long countByStatut(StatutDemande statut);

    /** Compteur restreint à un demandeur (EMPLOYE). */
    long countByDemandeurIdAndStatut(Long demandeurId, StatutDemande statut);

    /** Total déposé par un demandeur, indépendamment du statut. */
    long countByDemandeurId(Long demandeurId);

    /**
     * Un bien déjà livré pour une autre demande approuvée ne se redonne pas :
     * la demande tient le bien, et c'est elle qui en trace l'historique.
     */
    boolean existsByImmobilisationIdAndStatut(Long immobilisationId, StatutDemande statut);

    // ============ COMPTEURS DE RÉFÉRENCES (suppressions contrôlées) ============

    /** Demandes portant sur un bien — bloque la suppression du bien. */
    long countByImmobilisationId(Long immobilisationId);

    /** Demandes rattachées à un service — bloque la suppression du service. */
    long countByServiceId(Long serviceId);

    /** Demandes catégorisées — bloque la suppression de la catégorie. */
    long countByCategorieId(Long categorieId);

    /** Demandes traitées par un utilisateur — bloque la suppression du compte. */
    long countByTraiteeParId(Long traiteeParId);
}
