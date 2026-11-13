package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.exception.ConflitMetierException;
import com.auximad.gestion_immobilisation.model.Agence;
import com.auximad.gestion_immobilisation.model.Categorie;
import com.auximad.gestion_immobilisation.model.CompteurCodification;
import com.auximad.gestion_immobilisation.repository.CompteurCodificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Génération du code unique de codification :
 *
 *   [CodeAgence]/[Année sur 2 chiffres]/[CodeService]/[CodeComptable]/[TypeMateriel]/[N°]
 *   Exemple : 90/26/SIT/2183/UCB/002
 *
 * Service unique pour toute l'application : la logique était auparavant
 * dupliquée (copier-coller) dans {@code ImmobilisationMetier} et
 * {@code MouvementMetier}.
 *
 * Concurrence : le compteur (service, type) est lu sous verrou pessimiste
 * ({@code SELECT ... FOR UPDATE}), de sorte que deux créations simultanées sur
 * le même couple se sérialisent au lieu de se attribuer le même numéro.
 * La contrainte d'unicité (service_id, type_materiel) en base reste le dernier
 * rempart : si deux créations arrivent sur un compteur qui n'existe pas encore,
 * l'insertion perdante est détectée et renvoyée comme un conflit clair (409)
 * plutôt que comme un 500.
 *
 * Remarque : le numéro n'est pas réinitialisé chaque année (comportement
 * historique conservé pour ne pas casser les codes et les étiquettes déjà
 * imprimés), et le format %03d est conservé pour la même raison.
 */
@Service
@RequiredArgsConstructor
public class CodificationService {

    private final CompteurCodificationRepository compteurRepository;

    @Transactional
    public String genererCodeUnique(Agence agence,
                                    com.auximad.gestion_immobilisation.model.Service service,
                                    Categorie categorie,
                                    String typeMateriel) {
        String type = typeMateriel.toUpperCase();
        int numero = attribuerNumero(service.getId(), type);

        String annee = String.valueOf(LocalDateTime.now().getYear()).substring(2);
        return String.format("%s/%s/%s/%s/%s/%03d",
                agence.getCodeAgence(),
                annee,
                service.getCode(),
                categorie.getCodeComptable(),
                type,
                numero);
    }

    /**
     * Incrémente le compteur (service, type) sous verrou pessimiste.
     * Le verrou est conservé jusqu'à la fin de la transaction appelante :
     * toute création concurrente sur le même couple attend la fin du
     * traitement avant de lire son propre numéro.
     */
    private int attribuerNumero(Long serviceId, String type) {
        CompteurCodification compteur = compteurRepository
                .findByServiceIdAndTypeMaterielForUpdate(serviceId, type)
                .orElseGet(() -> creerCompteur(serviceId, type));

        int nouveauNumero = compteur.getDernierNumero() + 1;
        compteur.setDernierNumero(nouveauNumero);
        return nouveauNumero;
    }

    private CompteurCodification creerCompteur(Long serviceId, String type) {
        try {
            CompteurCodification compteur = CompteurCodification.builder()
                    .serviceId(serviceId)
                    .typeMateriel(type)
                    .dernierNumero(0)
                    .build();
            // flush immédiat : la contrainte d'unicité doit trancher ici, dans
            // la boucle de l'appelant, pas à la fermeture de la transaction.
            CompteurCodification saved = compteurRepository.save(compteur);
            compteurRepository.flush();
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflitMetierException(
                    "Création simultanée détectée sur la codification (" + type
                    + ") : le même bien est en cours de création ailleurs. "
                    + "Réessayez dans quelques instants.");
        }
    }
}
