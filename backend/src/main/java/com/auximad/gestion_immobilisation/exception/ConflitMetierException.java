package com.auximad.gestion_immobilisation.exception;

/**
 * Conflit métier : l'opération est refusée parce qu'elle enfreindrait une
 * règle de gestion — un enregistrement encore référencé, une opération
 * interdite sur une écriture comptable, une création concurrente...
 *
 * Traduite en HTTP 409 CONFLICT par le {@code GlobalExceptionHandler},
 * avec un message lisible destiné à l'utilisateur (contrairement au 500
 * « foreign key violation » que renvoyait auparavant une suppression maladroite).
 */
public class ConflitMetierException extends RuntimeException {

    public ConflitMetierException(String message) {
        super(message);
    }
}
