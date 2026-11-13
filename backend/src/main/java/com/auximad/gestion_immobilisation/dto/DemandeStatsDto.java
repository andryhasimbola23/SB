package com.auximad.gestion_immobilisation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compteurs de demandes pour l'en-tête de menu et le tableau de bord.
 *
 * Le périmètre suit l'appelant : un ADMIN obtient les totaux de l'entreprise,
 * un EMPLOYE uniquement les siens — sinon le compteur révélerait à l'employé
 * l'activité globale du service de traitement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeStatsDto {
    private long total;
    private long enAttente;
    private long approuvees;
    private long rejettees;
    private long annulees;
}
