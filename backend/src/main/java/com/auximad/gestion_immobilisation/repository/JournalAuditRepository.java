package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.JournalAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

    /**
     * Consultation filtrée — même idiom que le périmètre ailleurs :
     * {@code null} = filtre non appliqué. Les trois filtres sont indépendants.
     */
    @Query("SELECT j FROM JournalAudit j " +
           "WHERE (:action IS NULL OR j.action = :action) " +
           "AND (:entite IS NULL OR j.entite = :entite) " +
           "AND (:acteurId IS NULL OR j.acteur.id = :acteurId) " +
           "ORDER BY j.dateAction DESC")
    List<JournalAudit> rechercher(@Param("action") String action,
                                  @Param("entite") String entite,
                                  @Param("acteurId") Long acteurId);
}
