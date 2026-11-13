package com.auximad.gestion_immobilisation.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
        protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
        ) throws ServletException, IOException {
    
        // Autrefois, un court-circuit sur /api/auth/ évitait qu'un jeton expiré
        // fasse échouer la connexion. Le try/catch plus bas couvre désormais ce
        // cas proprement, et ce court-circuit empêchait surtout d'authentifier
        // GET /api/auth/me — dont le principal restait donc null.
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        // Un jeton expiré, malformé ou mal signé ne doit JAMAIS faire exploser la
        // requête : sans ce garde-fou, l'exception part dans le catch(Exception)
        // du GlobalExceptionHandler et le client reçoit un 500 au lieu d'un 401.
        // On laisse alors la chaîne sans authentification : SecurityConfig répond
        // 401 via son AuthenticationEntryPoint, ce qui déclenche la déconnexion
        // automatique côté client.
        String nomUtilisateur;
        UserDetails userDetails;
        try {
            nomUtilisateur = jwtUtils.extractUsername(jwt);
            // Couvre aussi le cas d'un compte supprimé entre-temps : le jeton est
            // encore valide mais l'utilisateur n'existe plus.
            userDetails = nomUtilisateur == null
                    ? null
                    : this.userDetailsService.loadUserByUsername(nomUtilisateur);
        } catch (JwtException | IllegalArgumentException
                 | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            logger.debug("JWT refusé pour " + request.getRequestURI() + " : " + e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (userDetails != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtUtils.isTokenValid(jwt, userDetails)) {
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }
}