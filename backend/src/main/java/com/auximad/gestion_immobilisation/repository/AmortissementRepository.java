package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Amortissement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmortissementRepository extends JpaRepository<Amortissement, Long> {

    List<Amortissement> findByImmobilisationIdOrderByAnneeAsc(Long immobilisationId);

    Optional<Amortissement> findByImmobilisationIdAndAnnee(Long immobilisationId, Integer annee);

    void deleteByImmobilisationId(Long immobilisationId);
}