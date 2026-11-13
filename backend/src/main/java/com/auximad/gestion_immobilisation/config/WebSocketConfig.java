package com.auximad.gestion_immobilisation.config;

import com.auximad.gestion_immobilisation.security.JwtHandshakeHandler;
import com.auximad.gestion_immobilisation.security.JwtHandshakeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Temps réel : STOMP over WebSocket.
 *
 * Endpoint {@code /ws} — le client s'y connecte avec son JWT en query
 * paramètre ({@code /ws?token=...}), validé par {@link JwtHandshakeInterceptor}
 * qui refuse le handshake sans jeton valide. Aucun SockJS : les navigateurs
 * visés (Chrome, Firefox, Edge) implémentent tous WebSocket natif.
 *
 * Broker simple en processus (aucun broker externe à déployer) :
 *  - {@code /user/queue/*} : files privées, résolues par utilisateur — c'est
 *    là que partent notifications et demandes ;
 *  - {@code /topic/*}      : broadcasts publics, disponibles pour de futurs
 *    événements (aucun usage actuel).
 *
 * Le préfixe {@code /user} résout chaque abonnement contre le principal posé au
 * handshake : un client ne peut s'abonner qu'à sa propre file, la sécurité des
 * rôles restant celle de {@code SecurityConfig} et du métier.
 *
 * Les heart-beats (10 s) exigent un planificateur : on réutilise celui que la
 * configuration broker fournit déjà ({@code messageBrokerTaskScheduler}),
 * sans lequel le démarrage échoue avec « Heartbeat values configured but no
 * TaskScheduler provided ». C'est ce qui permet de détecter en quelques
 * secondes une coupure silencieuse (wifi perdu, machine en veille) et de
 * déclencher la reconnexion automatique côté client.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    /**
     * Résolu paresseusement : le planificateur appartient à la configuration
     * broker déléguée, qui injecte CETTE classe en retour — l'injecter
     * directement au constructeur formerait un cycle (interdite par défaut).
     * {@code getObject()} n'est appelé qu'à l'exécution de
     * {@link #configureMessageBroker}, quand la déléguée est déjà construite.
     */
    private final ObjectProvider<TaskScheduler> messageBrokerTaskScheduler;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           @Qualifier("messageBrokerTaskScheduler")
                           ObjectProvider<TaskScheduler> messageBrokerTaskScheduler) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new JwtHandshakeHandler())
                .addInterceptors(jwtHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic")
                // scheduler d'abord, heart-beats ensuite : la valeur voulue
                // l'emporte quoi que dise la javadoc de setTaskScheduler.
                .setTaskScheduler(messageBrokerTaskScheduler.getObject())
                .setHeartbeatValue(new long[]{10000, 10000});
        registry.setUserDestinationPrefix("/user");
    }
}
