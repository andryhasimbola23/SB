package com.auximad.gestion_immobilisation.dto;

/**
 * Événement temps réel poussé vers la cloche d'un utilisateur.
 *
 * @param notification la notification fraîchement enregistrée (null si le
 *                     canal ne sert qu'à signaler un rafraîchissement)
 * @param nonLues      compteur exact des non-lues au moment de la publication —
 *                     le serveur reste la seule source de vérité du badge
 */
public record NotificationTempsReelDto(NotificationDto notification, long nonLues) {
}
