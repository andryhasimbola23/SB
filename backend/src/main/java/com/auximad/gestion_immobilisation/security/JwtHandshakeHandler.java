package com.auximad.gestion_immobilisation.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Associe l'utilisateur validé par {@link JwtHandshakeInterceptor} à la session
 * WebSocket.
 *
 * Sans principal, les destinations utilisateur ({@code /user/queue/...}) ne
 * peuvent pas être résolues : le client ne recevrait rien. Le nom du principal
 * est l'id de l'utilisateur, exactement la valeur que {@code RealtimeMetier}
 * utilise dans {@code convertAndSendToUser} — le serveur reste donc maître du
 * routage, le client ne pouvant s'abonner qu'à sa propre file.
 */
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object id = attributes.get(JwtHandshakeInterceptor.ATTRIBUT_UTILISATEUR_ID);
        if (id == null) {
            // Interceptor contourné ou handshake non validé : session anonyme,
            // incapable de recevoir la moindre destination utilisateur.
            return null;
        }
        return () -> String.valueOf(id);
    }
}
