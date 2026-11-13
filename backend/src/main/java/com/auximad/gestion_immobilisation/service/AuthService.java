package com.auximad.gestion_immobilisation.service;

import com.auximad.gestion_immobilisation.dto.LoginRequest;
import com.auximad.gestion_immobilisation.dto.LoginResponse;
import com.auximad.gestion_immobilisation.model.Utilisateur;
import com.auximad.gestion_immobilisation.repository.UtilisateurRepository;
import com.auximad.gestion_immobilisation.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final JournalAuditMetier journalAuditMetier;

    public LoginResponse login(LoginRequest request) {
        // 1. Authentifier l'utilisateur. Journalisé dans les deux sens : une
        // série de tentatives échouées est le signe d'une intrusion, et
        // l'absence de trace en pareil cas est la faille classique.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getNomUtilisateur(),
                            request.getMotDePasse()
                    )
            );
        } catch (AuthenticationException echec) {
            journalAuditMetier.enregistrer(null, JournalAuditMetier.CONNEXION_ECHOUEE,
                    JournalAuditMetier.ENTITE_SESSION, null,
                    "Tentative de connexion échouée pour « " + request.getNomUtilisateur() + " »");
            throw echec;
        }

        // 2. Récupérer l'utilisateur
        Utilisateur utilisateur = utilisateurRepository.findByNomUtilisateur(request.getNomUtilisateur())
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        // 3. Générer le token JWT
        String token = jwtUtils.generateToken(utilisateur);

        journalAuditMetier.enregistrer(utilisateur, JournalAuditMetier.CONNEXION,
                JournalAuditMetier.ENTITE_SESSION, null,
                "Connexion de " + utilisateur.getPrenom() + " " + utilisateur.getNom()
                        + " (" + utilisateur.getRole().name() + ")");

        // 4. Retourner la réponse
        return reponseUtilisateur(utilisateur, token);
    }

    /**
     * Payload de session, partagé entre la connexion et la validation de session
     * ({@code GET /api/auth/me}). Les deux doivent produire exactement la même
     * forme d'objet : c'est lui que le client recopie dans son localStorage et
     * que {@code ProtectedRoute} relit pour décider d'autoriser l'accès.
     *
     * @param jeton le JWT à retourner, ou {@code null} lors d'une simple
     *              validation de session — le client conserve déjà le sien
     */
    public LoginResponse reponseUtilisateur(Utilisateur utilisateur, String jeton) {
        return new LoginResponse(
                utilisateur.getId(),
                jeton,
                utilisateur.getNomUtilisateur(),
                utilisateur.getRole().name(),
                utilisateur.getPrenom() + " " + utilisateur.getNom(),
                // `service` est en FetchType.EAGER : accessible même hors
                // transaction, y depuis le principal injecté par le filtre JWT.
                utilisateur.getService() != null ? utilisateur.getService().getId() : null,
                utilisateur.getService() != null ? utilisateur.getService().getNomService() : null
        );
    }
}