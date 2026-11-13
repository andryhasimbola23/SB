package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.CompteurCodification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompteurCodificationRepository extends JpaRepository<CompteurCodification, Long> {

    Optional<CompteurCodification> findByServiceIdAndTypeMateriel(Long serviceId, String typeMateriel);

    /**
     * Lecture sous verrou pessimiste (SELECT ... FOR UPDATE) : deux
     * codifications concurrentes sur le même couple (service, type) se
     * sérialisent au lieu de s'attribuer le même numéro.
     *
     * @Query explicite obligatoire : sans elle, Spring Data tenterait d'interpréter
     * le « ForUpdate » du nom comme une propriété (« typeMateriel.forUpdate »).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompteurCodification c " +
           "WHERE c.serviceId = :serviceId AND c.typeMateriel = :typeMateriel")
    Optional<CompteurCodification> findByServiceIdAndTypeMaterielForUpdate(
            @Param("serviceId") Long serviceId,
            @Param("typeMateriel") String typeMateriel);
}