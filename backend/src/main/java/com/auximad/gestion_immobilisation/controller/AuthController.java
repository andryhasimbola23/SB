package com.auximad.gestion_immobilisation.controller;

import com.auximad.gestion_immobilisation.dto.ChangePasswordDto;
import com.auximad.gestion_immobilisation.dto.LoginRequest;
import com.auximad.gestion_immobilisation.dto.LoginResponse;
import com.auximad.gestion_immobilisation.dto.ProfilDto;
import com.auximad.gestion_immobilisation.dto.UtilisateurDto;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.service.AuthService;
import com.auximad.gestion_immobilisation.service.UtilisateurMetier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthService authService;
    private final UtilisateurMetier utilisateurMetier;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * GET /api/auth/me — validation de session.
     *
     * Permet au client de confirmer, au démarrage de l'application, que le jeton
     * stocké dans son localStorage est encore réellement valable : sans cet
     * appel, l'interface se fiait à un objet recopié localement et affichait un
     * tableau de bord dont chaque requête échouait.
     *
     * Renvoie le même objet que /login, de sorte que la forme du `user` stocké
     * côté client reste identique après une validation.
     */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> moi(
            @AuthenticationPrincipal Utilisateur utilisateur,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // Aucune authentification établie (jeton absent, expiré ou refusé).
        // Sans ce garde-fou, le principal serait null et la requête finirait en 500.
        if (utilisateur == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String jeton = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : null;
        return ResponseEntity.ok(authService.reponseUtilisateur(utilisateur, jeton));
    }

    /**
     * POST /api/auth/change-password — changement du mot de passe de
     * l'utilisateur CONNECTÉ.
     *
     * Contrairement à /api/utilisateurs/{id}/change-password (réservé à
     * l'ADMIN mais exigeant l'ancien mot de passe, donc inutilisable pour un
     * employé), cet endpoint s'applique au porteur du jeton : chacun peut
     * changer son propre mot de passe en fournissant l'ancien.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changerMonMotDePasse(
            @AuthenticationPrincipal Utilisateur utilisateur,
            @Valid @RequestBody ChangePasswordDto dto) {
        if (utilisateur == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        utilisateurMetier.changerMotDePassePropre(utilisateur, dto);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/auth/profil — fiche complète du compte CONNECTÉ.
     *
     * /utilisateurs/{id} est réservé à l'ADMIN : sans cet endpoint, un employé
     * ne pourrait jamais consulter sa propre fiche (email, téléphone, photo,
     * service, agence, date de création).
     */
    @GetMapping("/profil")
    public ResponseEntity<UtilisateurDto> profil(@AuthenticationPrincipal Utilisateur utilisateur) {
        if (utilisateur == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(utilisateurMetier.profilDe(utilisateur));
    }

    /**
     * PATCH /api/auth/profil — le TITULAIRE met à jour ses propres
     * coordonnées (email, téléphone, photo). Rien d'autre n'est applicable :
     * nom, rôle, service et agence restent des prérogatives de l'ADMIN. Chaque
     * modification laisse une trace au journal.
     */
    @PatchMapping("/profil")
    public ResponseEntity<UtilisateurDto> mettreAJourProfil(
            @AuthenticationPrincipal Utilisateur utilisateur,
            @Valid @RequestBody ProfilDto dto) {
        if (utilisateur == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(utilisateurMetier.mettreAJourProfil(utilisateur, dto));
    }
}