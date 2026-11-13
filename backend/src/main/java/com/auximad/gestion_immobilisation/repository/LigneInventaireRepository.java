package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.LigneInventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LigneInventaireRepository extends JpaRepository<LigneInventaire, Long> {

    List<LigneInventaire> findByCampagneIdOrderByDateScanDesc(Long campagneId);

    Optional<LigneInventaire> findByCampagneIdAndImmobilisationId(Long campagneId, Long immobilisationId);

    long countByCampagneId(Long campagneId);

    long countByCampagneIdAndEtatConstate(Long campagneId, String etatConstate);

    /** Constats portés sur un bien — bloque la suppression du bien. */
    long countByImmobilisationId(Long immobilisationId);

    /**
     * Statistiques de plusieurs campagnes en une seule requête (GROUP BY),
     * au lieu de quatre compteurs par campagne (N+1) lors de la liste.
     * Retourne : [campagneId, etatConstate, nombre]
     */
    @Query("SELECT l.campagne.id, l.etatConstate, COUNT(l) FROM LigneInventaire l " +
           "WHERE l.campagne.id IN :campagneIds " +
           "GROUP BY l.campagne.id, l.etatConstate")
    List<Object[]> compterParCampagneEtEtat(@Param("campagneIds") List<Long> campagneIds);
}