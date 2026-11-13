package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.CampagneInventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampagneInventaireRepository extends JpaRepository<CampagneInventaire, Long> {

    List<CampagneInventaire> findAllByOrderByDateCreationDesc();

    List<CampagneInventaire> findByStatutOrderByDateCreationDesc(String statut);

    long countByStatut(String statut);

    // ============ COMPTEURS DE RÉFÉRENCES (suppressions contrôlées) ============

    long countByUtilisateurId(Long utilisateurId);

    long countByAgenceId(Long agenceId);
}