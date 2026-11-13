package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Agence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgenceRepository extends JpaRepository<Agence, Long> {

    Optional<Agence> findByCodeAgence(String codeAgence);

    boolean existsByCodeAgence(String codeAgence);

    List<Agence> findByActifTrue();

    @Query("SELECT a FROM Agence a WHERE " +
           "LOWER(a.nomAgence) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.codeAgence) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.villeAgence) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Agence> searchByKeyword(@Param("keyword") String keyword);
}