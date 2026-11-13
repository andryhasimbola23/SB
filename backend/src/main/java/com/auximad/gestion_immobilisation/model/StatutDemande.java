package com.auximad.gestion_immobilisation.model;

/**
 * Cycle de vie d'une demande d'immobilisation.
 *
 * Contrairement aux statuts stockés en String ailleurs dans le projet
 * (Mouvement.typeMouvement, Immobilisation.statut), celui-ci porte une machine à
 * transitions : on n'approuve qu'une demande encore EN_ATTENTE. Un enum rend ici
 * les fautes de frappe impossibles et les transitions vérifiables au compilateur.
 */
public enum StatutDemande {

    /** Déposée par un employé, en attente d'une décision. */
    EN_ATTENTE,

    /** Acceptée par un administrateur. */
    APPROUVEE,

    /** Refusée par un administrateur (motif obligatoire). */
    REJETTEE,

    /** Retirée par l'employé lui-même, avant toute décision. */
    ANNULEE;

    public boolean estEnAttente() {
        return this == EN_ATTENTE;
    }

    /** Une demande dont l'issue est déjà connue (plus modifiable). */
    public boolean estDecidee() {
        return this == APPROUVEE || this == REJETTEE;
    }
}
