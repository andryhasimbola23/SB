package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Mouvement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Les mouvements sont filtrés via le service de l'immobilisation concernée.
 * Même convention que {@link ImmobilisationRepository} : {@code serviceId}
 * vaut {@code null} (ADMIN), l'id du service, ou -1 (périmètre vide).
 */
@Repository
public interface MouvementRepository extends JpaRepository<Mouvement, Long> {

    @Query("SELECT m FROM Mouvement m " +
           "WHERE m.immobilisation.id = :immobilisationId " +
           "AND (:serviceId IS NULL OR m.immobilisation.service.id = :serviceId) " +
           "ORDER BY m.dateMouvement DESC")
    List<Mouvement> findByImmobilisationIdOrderByDateMouvementDesc(
            @Param("immobilisationId") Long immobilisationId,
            @Param("serviceId") Long serviceId);

    @Query("SELECT m FROM Mouvement m " +
           "WHERE (:serviceId IS NULL OR m.immobilisation.service.id = :serviceId) " +
           "ORDER BY m.dateMouvement DESC")
    List<Mouvement> findAllByOrderByDateMouvementDesc(@Param("serviceId") Long serviceId);

    @Query("SELECT m FROM Mouvement m " +
           "WHERE m.typeMouvement = :typeMouvement " +
           "AND (:serviceId IS NULL OR m.immobilisation.service.id = :serviceId) " +
           "ORDER BY m.dateMouvement DESC")
    List<Mouvement> findByTypeMouvementOrderByDateMouvementDesc(
            @Param("typeMouvement") String typeMouvement,
            @Param("serviceId") Long serviceId);

    @Query("SELECT COUNT(m) FROM Mouvement m " +
           "WHERE MONTH(m.dateMouvement) = :mois AND YEAR(m.dateMouvement) = :annee " +
           "AND (:serviceId IS NULL OR m.immobilisation.service.id = :serviceId)")
    long countByMoisAndAnnee(@Param("mois") int mois,
                             @Param("annee") int annee,
                             @Param("serviceId") Long serviceId);

    @Query("SELECT m FROM Mouvement m " +
           "WHERE (:serviceId IS NULL OR m.immobilisation.service.id = :serviceId) " +
           "ORDER BY m.dateMouvement DESC LIMIT 5")
    List<Mouvement> findDerniersMouvements(@Param("serviceId") Long serviceId);

    /**
     * Livraisons d'un lot de demandes.
     * Un seul aller-retour pour toute une liste, plutôt qu'une requête par ligne
     * (N+1) : le lien demande -> mouvement s'affiche dans la liste des demandes.
     */
    @Query("SELECT m FROM Mouvement m WHERE m.demande.id IN :ids")
    List<Mouvement> findByDemandeIdIn(@Param("ids") List<Long> ids);

    // ============ COMPTEURS DE RÉFÉRENCES (suppressions contrôlées) ============

    /** Mouvements tracés sur un bien (jamais supprimables, mais bloquants pour une suppression du bien). */
    long countByImmobilisationId(Long immobilisationId);

    /** Écritures d'un utilisateur (auteur des mouvements) — bloque la suppression du compte. */
    long countByUtilisateurId(Long utilisateurId);
}
