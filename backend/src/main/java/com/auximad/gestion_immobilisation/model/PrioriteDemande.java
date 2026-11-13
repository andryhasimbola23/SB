package com.auximad.gestion_immobilisation.model;

/**
 * Niveau d'urgence d'une demande d'immobilisation.
 */
public enum PrioriteDemande {

    /** Demande ordinaire, traitée dans le flux habituel. */
    NORMALE,

    /** Matériel requis rapidement (indisponibilité, panne, date imminente). */
    URGENTE
}
