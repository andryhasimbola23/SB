package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByCode(String code);

    boolean existsByCode(String code);

    List<Service> findByActifTrue();

    List<Service> findByAgenceId(Long agenceId);

    /** Services rattachés à une agence — bloque la suppression de l'agence. */
    long countByAgenceId(Long agenceId);

    @Query("SELECT s FROM Service s WHERE " +
           "LOWER(s.nomService) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Service> searchByKeyword(@Param("keyword") String keyword);
}