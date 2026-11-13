package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Immobilisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Toutes les méthodes de lecture portent un paramètre {@code serviceId} à
 * appliquer sous la forme {@code (:serviceId IS NULL OR ...)} :
 *
 *  - {@code null}  -> aucun périmètre (ADMIN)
 *  - id de service -> périmètre d'un EMPLOYE
 *  - -1            -> périmètre vide (EMPLOYE sans service)
 *
 * Le fait que ce paramètre soit obligatoire empêche d'écrire une requête
 * d'oubliant le filtrage.
 */
@Repository
public interface ImmobilisationRepository extends JpaRepository<Immobilisation, Long> {

    // ============ MÉTHODES DE BASE ============

    Optional<Immobilisation> findByCodeUnique(String codeUnique);

    boolean existsByCodeUnique(String codeUnique);

    @Query("SELECT i FROM Immobilisation i " +
           "WHERE (:serviceId IS NULL OR i.service.id = :serviceId) " +
           "ORDER BY i.dateCreation DESC")
    List<Immobilisation> findAllDansPerimetre(@Param("serviceId") Long serviceId);

    // ============ COMPTEURS DE RÉFÉRENCES (suppressions contrôlées) ============
    // Utilisés avant toute suppression pour renvoyer un 409 explicite plutôt
    // qu'une violation de clé étrangère (500) en fin de transaction.

    long countByAgenceId(Long agenceId);

    long countByServiceId(Long serviceId);

    long countByCategorieId(Long categorieId);

    long countByFournisseurId(Long fournisseurId);

    @Query("SELECT COUNT(i) FROM Immobilisation i " +
           "WHERE (:serviceId IS NULL OR i.service.id = :serviceId)")
    long countDansPerimetre(@Param("serviceId") Long serviceId);

    @Query("SELECT i FROM Immobilisation i " +
           "WHERE i.statut = :statut " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    List<Immobilisation> findByStatut(@Param("statut") String statut,
                                      @Param("serviceId") Long serviceId);

    @Query("SELECT COUNT(i) FROM Immobilisation i " +
           "WHERE i.statut = :statut " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    long countByStatut(@Param("statut") String statut,
                       @Param("serviceId") Long serviceId);

    @Query("SELECT COUNT(i) FROM Immobilisation i " +
           "WHERE i.etatPhysique = :etatPhysique " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    long countByEtatPhysique(@Param("etatPhysique") String etatPhysique,
                             @Param("serviceId") Long serviceId);

    @Query("SELECT i FROM Immobilisation i WHERE " +
           "(LOWER(i.designation) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(i.codeUnique) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(i.marque) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    List<Immobilisation> searchByKeyword(@Param("keyword") String keyword,
                                         @Param("serviceId") Long serviceId);

    // ============ DASHBOARD : STATISTIQUES ============

    /**
     * Valeur totale du parc (uniquement biens actifs).
     */
    @Query("SELECT COALESCE(SUM(i.valeurAchat), 0) FROM Immobilisation i " +
           "WHERE i.statut = 'ACTIF' " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    BigDecimal getValeurTotaleParc(@Param("serviceId") Long serviceId);

    /**
     * Données nécessaires au calcul de la VNC de chaque bien actif, en une
     * seule requête : [valeurAchat, tauxAmortissement, dateMiseService].
     *
     * Remplace le chargement de tous les actifs suivi d'un findById par bien
     * (N+1) qu'effectuait le tableau de bord pour additionner les VNC.
     */
    @Query("SELECT i.valeurAchat, i.tauxAmortissement, i.dateMiseService " +
           "FROM Immobilisation i " +
           "WHERE i.statut = 'ACTIF' " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    List<Object[]> findDonneesVncDansPerimetre(@Param("serviceId") Long serviceId);

    /**
     * Nombre de biens créés dans un mois donné (dashboard), au lieu de
     * charger tout le parc puis de filtrer en mémoire.
     */
    @Query("SELECT COUNT(i) FROM Immobilisation i " +
           "WHERE MONTH(i.dateCreation) = :mois AND YEAR(i.dateCreation) = :annee " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    long countNouveauxBiens(@Param("mois") int mois,
                            @Param("annee") int annee,
                            @Param("serviceId") Long serviceId);

    /**
     * Nombre de biens par agence, en une seule requête pour une liste
     * d'agences (statistiques des campagnes d'inventaire).
     * Retourne : [agenceId, nombre]
     */
    @Query("SELECT a.id, COUNT(i) FROM Immobilisation i JOIN i.agence a " +
           "WHERE a.id IN :agenceIds GROUP BY a.id")
    List<Object[]> compterParAgence(@Param("agenceIds") List<Long> agenceIds);

    /**
     * Répartition des biens par catégorie.
     * Retourne : [nomCategorie, nombre, sommeValeurs]
     */
    @Query("SELECT c.nomCategorie, COUNT(i), COALESCE(SUM(i.valeurAchat), 0) " +
           "FROM Immobilisation i JOIN i.categorie c " +
           "WHERE (:serviceId IS NULL OR i.service.id = :serviceId) " +
           "GROUP BY c.nomCategorie ORDER BY COUNT(i) DESC")
    List<Object[]> getRepartitionParCategorie(@Param("serviceId") Long serviceId);

    /**
     * Répartition des biens par service.
     * Retourne : [codeService, nomService, nombre, sommeValeurs]
     *
     * Le sigle part avec le nom : c'est lui qui tient sur l'axe d'un graphique,
     * le nom complet (souvent long) restant affiché dans l'infobulle.
     */
    @Query("SELECT s.code, s.nomService, COUNT(i), COALESCE(SUM(i.valeurAchat), 0) " +
           "FROM Immobilisation i JOIN i.service s " +
           "WHERE (:serviceId IS NULL OR i.service.id = :serviceId) " +
           "GROUP BY s.code, s.nomService ORDER BY COUNT(i) DESC")
    List<Object[]> getRepartitionParService(@Param("serviceId") Long serviceId);

    /**
     * Valeur du parc par agence — biens actifs seulement, triés du plus
     * valorisé au moins : la question posée est « où est l'argent du parc ».
     * Retourne : [nomAgence, nombre, sommeValeurs]
     */
    @Query("SELECT a.nomAgence, COUNT(i), COALESCE(SUM(i.valeurAchat), 0) " +
           "FROM Immobilisation i JOIN i.agence a " +
           "WHERE i.statut = 'ACTIF' " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId) " +
           "GROUP BY a.nomAgence ORDER BY SUM(i.valeurAchat) DESC")
    List<Object[]> getRepartitionParAgence(@Param("serviceId") Long serviceId);

    /**
     * Actifs dont la mise en service est antérieure à un seuil — indicateur
     * de parc vieillissant pour le tableau de bord.
     */
    @Query("SELECT COUNT(i) FROM Immobilisation i " +
           "WHERE i.statut = 'ACTIF' AND i.dateMiseService < :seuil " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId)")
    long countActifsAnciens(@Param("seuil") LocalDateTime seuil,
                            @Param("serviceId") Long serviceId);

    /**
     * Les 5 dernières acquisitions (par date de création).
     */
    @Query("SELECT i FROM Immobilisation i " +
           "WHERE (:serviceId IS NULL OR i.service.id = :serviceId) " +
           "ORDER BY i.dateCreation DESC LIMIT 5")
    List<Immobilisation> getDernieresAcquisitions(@Param("serviceId") Long serviceId);

    /**
     * Biens livrables sur approbation d'une demande d'immobilisation.
     *
     * Porte {@code serviceId} comme toutes les méthodes de lecture de cette
     * classe, même si l'appelant actuel est un ADMIN (périmètre {@code null}) :
     * la règle du périmètre est ainsi déjà posée si l'endpoint était un jour élargi.
     *
     * Ne liste que ce que le transfert saura effectivement traiter : bien actif,
     * non hors service, rattaché à un service, une agence et une catégorie —
     * les trois derniers étant nécessaires à la réémission du code unique — et
     * pas déjà remis sur une autre demande approuvée, que le serveur refuserait.
     */
    @Query("SELECT i FROM Immobilisation i " +
           "WHERE i.statut = 'ACTIF' " +
           "AND i.etatPhysique <> 'HORS_SERVICE' " +
           "AND i.service IS NOT NULL AND i.agence IS NOT NULL AND i.categorie IS NOT NULL " +
           "AND NOT EXISTS (SELECT 1 FROM DemandeImmobilisation d " +
           "               WHERE d.immobilisation.id = i.id " +
           "               AND d.statut = com.auximad.gestion_immobilisation.model.StatutDemande.APPROUVEE) " +
           "AND (:serviceId IS NULL OR i.service.id = :serviceId) " +
           "AND (:categorieId IS NULL OR i.categorie.id = :categorieId) " +
           "AND (LOWER(i.codeUnique) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(i.designation) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(i.marque) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(i.service.nomService) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY i.designation")
    List<Immobilisation> rechercherCandidats(@Param("serviceId") Long serviceId,
                                             @Param("categorieId") Long categorieId,
                                             @Param("q") String q);
}
