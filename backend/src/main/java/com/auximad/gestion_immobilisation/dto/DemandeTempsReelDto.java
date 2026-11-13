package com.auximad.gestion_immobilisation.dto;

/**
 * Événement temps réel signalant qu'une demande a changé : le client intéressé
 * recharge alors sa liste sans actualisation de la page.
 *
 * @param evenement  NOUVELLE_DEMANDE, DEMANDE_DECIDEE ou DEMANDE_ANNULEE
 *                   (constantes de {@link com.auximad.gestion_immobilisation.service.RealtimeMetier})
 * @param demandeId  identifiant de la demande concernée
 */
public record DemandeTempsReelDto(String evenement, Long demandeId) {
}
