package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByNomUtilisateur(String nomUtilisateur);

    boolean existsByNomUtilisateur(String nomUtilisateur);

    boolean existsByEmail(String email);

    List<Utilisateur> findByRole(Role role);

    List<Utilisateur> findByActifTrue();

    @Query("SELECT u FROM Utilisateur u WHERE " +
           "LOWER(u.nomUtilisateur) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.prenom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Utilisateur> searchByKeyword(@Param("keyword") String keyword);

    // ============ COMPTEURS DE RÉFÉRENCES (suppressions contrôlées) ============

    long countByAgenceId(Long agenceId);

    long countByServiceId(Long serviceId);
}