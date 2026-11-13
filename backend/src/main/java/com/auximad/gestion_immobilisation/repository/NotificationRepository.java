package com.auximad.gestion_immobilisation.repository;

import com.auximad.gestion_immobilisation.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDestinataireIdOrderByDateCreationDesc(Long destinataireId);

    /** Compteur du badge (cloche) — index sur destinataire_id + lue. */
    long countByDestinataireIdAndLueFalse(Long destinataireId);

    /**
     * « Tout marquer comme lu » en une seule requête : pas besoin de charger
     * les lignes pour les mettre à jour une par une.
     *
     * @return le nombre de notifications effectivement marquées.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.lue = true, n.dateLue = :now " +
           "WHERE n.destinataire.id = :destinataireId AND n.lue = false")
    int marquerToutesLues(@Param("destinataireId") Long destinataireId,
                          @Param("now") LocalDateTime now);
}
