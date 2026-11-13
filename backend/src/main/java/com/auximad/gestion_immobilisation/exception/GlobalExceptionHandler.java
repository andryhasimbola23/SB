package com.auximad.gestion_immobilisation.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Règle de gestion enfreinte (référence encore utilisée, opération
     * interdite sur une écriture comptable, création concurrente...) :
     * le message métier est destiné à l'utilisateur.
     */
    @ExceptionHandler(ConflitMetierException.class)
    public ResponseEntity<Map<String, Object>> handleConflitMetier(ConflitMetierException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Deux utilisateurs ont modifié la même fiche : la seconde écriture est
     * refusée (verrouillage optimiste @Version) au lieu d'écraser la première.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT,
                "Ces données ont été modifiées par un autre utilisateur. "
                + "Rechargez la page puis réessayez.");
    }

    /**
     * Contrainte d'intégrité (unicité, clé étrangère) : traduite en 409 avec
     * un message lisible, jamais en 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violation d'intégrité des données : {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT,
                "Opération refusée : ces données sont déjà utilisées ou entrent en conflit "
                + "avec une donnée existante.");
    }

    /**
     * Échec d'authentification (mauvais mot de passe, compte désactivé ou
     * verrouillé). C'est un refus d'accès attendu, pas une erreur serveur :
     * sans ce gestionnaire, l'exception remonte jusqu'au repli générique et le
     * client reçoit un 500 trompeur — et le serveur journalise une « erreur
     * interne » pour ce qui n'est qu'une saisie erronée. Le détail du refus
     * reste côté serveur (journal F5) ; le client ne sait que « identifiants
     * refusés », sans distinction mot de passe / compte, pour ne pas guider
     * une tentative d'intrusion.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentification refusée : {}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Identifiants refusés.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Dernier recours : message générique côté client. Le détail (stacktrace)
     * reste dans les logs serveur — l'ancienne version renvoyait
     * {@code ex.getMessage()} au client, qui pouvait contenir le nom de table,
     * la requête SQL ou le chemin interne en cause.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        log.error("Erreur interne non gérée", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue. Vérifiez vos données ou réessayez plus tard.");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
