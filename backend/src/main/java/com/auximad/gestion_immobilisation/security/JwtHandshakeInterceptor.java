package com.auximad.gestion_immobilisation.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authentification du handshake WebSocket (endpoint {@code /ws}).
 *
 * Un WebSocket navigateur ne peut pas porter d'en-tête {@code Authorization} :
 * le JWT voyage donc en query paramètre ({@code /ws?token=...}), relu à chaque
 * tentative de (re)connexion par le client.
 *
 * La validation reproduit {@link JwtAuthenticationFilter} : signature, expiration
 * et existence de l'utilisateur — un jeton invalide ou absent refuse le
 * handshake (401) avant même l'ouverture de la socket, et l'utilisateur validé
 * est consigné dans les attributs pour {@link JwtHandshakeHandler}.
 *
 * C'est le seul point d'entrée authentifié du canal temps réel : aucune règle
 * de sécurité HTTP n'est contournée, le filtrage par rôle restant affaire de
 * {@code SecurityConfig} et du métier.
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Identifiant de l'utilisateur validé — lu par {@link JwtHandshakeHandler}. */
    public static final String ATTRIBUT_UTILISATEUR_ID = "utilisateurId";

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest requete)) {
            refuser(response);
            return false;
        }

        HttpServletRequest servlet = requete.getServletRequest();
        String jeton = servlet.getParameter("token");
        if (jeton == null || jeton.isBlank()) {
            refuser(response);
            return false;
        }

        try {
            String nomUtilisateur = jwtUtils.extractUsername(jeton);
            UserDetails details = nomUtilisateur == null
                    ? null
                    : userDetailsService.loadUserByUsername(nomUtilisateur);
            if (details == null || !jwtUtils.isTokenValid(jeton, details)) {
                refuser(response);
                return false;
            }
            attributes.put(ATTRIBUT_UTILISATEUR_ID,
                    details instanceof com.auximad.gestion_immobilisation.model.Utilisateur utilisateur
                            ? utilisateur.getId()
                            : null);
            return true;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // Jeton expiré, malformé, mal signé ou compte disparu : refus silencieux,
            // exactement comme le filtre HTTP — pas d'information fuitée.
            refuser(response);
            return false;
        }
    }

    private void refuser(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Rien à nettoyer : les attributs vivent avec la session WebSocket.
    }
}
