package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {

    List<Fournisseur> findByActifTrue();

    boolean existsByNomFournisseur(String nomFournisseur);

    @Query("SELECT f FROM Fournisseur f WHERE " +
           "LOWER(f.nomFournisseur) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(f.emailFournisseur) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(f.telephoneFournisseur) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Fournisseur> searchByKeyword(@Param("keyword") String keyword);
}