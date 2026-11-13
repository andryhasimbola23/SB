package com.auximad.gestion_immobilisation.security;

import com.auximad.gestion_immobilisation.model.Utilisateur;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Handshake WebSocket : le JWT en query paramètre est la seule porte d'entrée
 * du canal temps réel — sans lui, aucun client n'ouvre de socket.
 */
class JwtHandshakeInterceptorTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtHandshakeInterceptor intercepteur =
            new JwtHandshakeInterceptor(jwtUtils, userDetailsService);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);

    private ServletServerHttpRequest requeteAvecToken(String valeur) {
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getParameter("token")).thenReturn(valeur);
        return new ServletServerHttpRequest(servlet);
    }

    @Test
    @DisplayName("Jeton valide : handshake accepté et utilisateur consigné dans les attributs")
    void jetonValideAccepte() {
        Utilisateur utilisateur = Utilisateur.builder().id(42L).nomUtilisateur("alice").build();
        when(jwtUtils.extractUsername("jeton-ok")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(utilisateur);
        when(jwtUtils.isTokenValid("jeton-ok", utilisateur)).thenReturn(true);

        Map<String, Object> attributs = new HashMap<>();
        boolean accepte = intercepteur.beforeHandshake(
                requeteAvecToken("jeton-ok"), response, null, attributs);

        assertTrue(accepte, "un jeton valide doit ouvrir la socket");
        assertEquals(42L, attributs.get(JwtHandshakeInterceptor.ATTRIBUT_UTILISATEUR_ID),
                "l'id de l'utilisateur doit alimenter le principal de session");
        verify(response, never()).setStatusCode(any(HttpStatus.class));
    }

    @Test
    @DisplayName("Sans jeton : 401, aucune socket ouverte")
    void sansJetonRefuse() {
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getParameter("token")).thenReturn(null);

        Map<String, Object> attributs = new HashMap<>();
        boolean accepte = intercepteur.beforeHandshake(
                new ServletServerHttpRequest(servlet), response, null, attributs);

        assertFalse(accepte);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertNull(attributs.get(JwtHandshakeInterceptor.ATTRIBUT_UTILISATEUR_ID));
    }

    @Test
    @DisplayName("Jeton expiré ou malformé : 401, sans information fuiteée")
    void jetonInvalideRefuse() {
        when(jwtUtils.extractUsername("pire-jeton")).thenThrow(new JwtException("expiré"));

        boolean accepte = intercepteur.beforeHandshake(
                requeteAvecToken("pire-jeton"), response, null, new HashMap<>());

        assertFalse(accepte);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Compte disparu : 401 (même garde-fou que le filtre HTTP)")
    void compteInexistantRefuse() {
        when(jwtUtils.extractUsername("fantome")).thenReturn("fantome");
        when(userDetailsService.loadUserByUsername("fantome"))
                .thenThrow(new org.springframework.security.core.userdetails
                        .UsernameNotFoundException("absent"));

        boolean accepte = intercepteur.beforeHandshake(
                requeteAvecToken("fantome"), response, null, new HashMap<>());

        assertFalse(accepte);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
