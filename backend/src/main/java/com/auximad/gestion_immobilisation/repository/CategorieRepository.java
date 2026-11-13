package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    Optional<Categorie> findByCodeComptable(String codeComptable);

    boolean existsByCodeComptable(String codeComptable);

    List<Categorie> findByActifTrue();

    List<Categorie> findByTypeImmobilisation(String typeImmobilisation);

    @Query("SELECT c FROM Categorie c WHERE " +
           "LOWER(c.nomCategorie) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.codeComptable) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.libelle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Categorie> searchByKeyword(@Param("keyword") String keyword);
}