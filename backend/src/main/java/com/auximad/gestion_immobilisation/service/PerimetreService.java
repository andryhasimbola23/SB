package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.exception.ResourceNotFoundException;
import com.auximad.gestion_immobilisation.model.Immobilisation;
import com.auximad.gestion_immobilisation.model.Role;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Périmètre de consultation d'un utilisateur.
 *
 * Règle : un ADMIN consulte tout ; un EMPLOYE ne consulte que les
 * immobilisations de son service, et les mouvements de ces immobilisations.
 *
 * La valeur retournée par {@link #serviceIdDe} est faite pour être passée
 * telle quelle aux requêtes JPQL sous la forme :
 *
 *     WHERE (:serviceId IS NULL OR i.service.id = :serviceId)
 *
 * de sorte qu'une seule méthode de repository serve les deux rôles, et qu'aucun
 * chemin de lecture ne puisse être « oublié » dans le filtrage.
 */
@Component
public class PerimetreService {

    /**
     * Périmètre vide. Aucun service ne porte cet identifiant (auto-incrément
     * démarrant à 1), donc la condition de périmètre ne peut jamais être
     * satisfaite.
     *
     * Sert pour un EMPLOYE dont le service n'est pas encore affecté : il doit
     * ne rien voir (fail-closed) — et surtout pas tout voir, ce qui arriverait
     * si on retournait simplement null.
     */
    public static final Long AUCUN_SERVICE = -1L;

    /**
     * Identifiant de service servant de périmètre.
     *
     * @return {@code null} pour un périmètre illimité (ADMIN), l'id du service
     *         pour un EMPLOYE rattaché, ou {@link #AUCUN_SERVICE} pour un
     *         EMPLOYE sans service (ne voit rien).
     */
    public Long serviceIdDe(Utilisateur user) {
        if (user == null || user.getRole() == Role.ADMIN) {
            return null;
        }
        if (user.getService() == null || user.getService().getId() == null) {
            return AUCUN_SERVICE;
        }
        return user.getService().getId();
    }

    /**
     * Vrai si l'utilisateur peut consulter l'immobilisation passée.
     */
    public boolean peutVoir(Immobilisation immo, Utilisateur user) {
        Long serviceId = serviceIdDe(user);
        if (serviceId == null) {
            return true; // ADMIN
        }
        return immo != null
                && immo.getService() != null
                && serviceId.equals(immo.getService().getId());
    }

    /**
     * Charge une immobilisation et vérifie qu'elle appartient au périmètre.
     *
     * Lève {@link ResourceNotFoundException} — avec le même message que si
     * l'immobilisation n'existait pas, pour ne pas révéler par énumération
     * l'existence d'un bien hors périmètre.
     */
    public Immobilisation exigerAccessible(Optional<Immobilisation> immo, Utilisateur user) {
        Immobilisation imm = immo.orElseThrow(() ->
                new ResourceNotFoundException("Immobilisation non trouvée"));
        if (!peutVoir(imm, user)) {
            throw new ResourceNotFoundException("Immobilisation non trouvée");
        }
        return imm;
    }
}
