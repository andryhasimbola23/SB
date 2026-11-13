package com.auximad.gestion_immobilisation.config;

import com.auximad.gestion_immobilisation.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // ============================================
                // ROUTES PUBLIQUES
                // ============================================
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // Handshake WebSocket : le JWT y est validé par
                // JwtHandshakeInterceptor (query param, le WS navigateur n'a pas
                // d'en-tête Authorization) — sans jeton valide, 401 avant l'upgrade.
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/error").permitAll()

                // ============================================
                // ROUTES ADMIN UNIQUEMENT
                // ============================================
                .requestMatchers("/api/utilisateurs/**").hasRole("ADMIN")

                // Agences
                .requestMatchers(HttpMethod.POST, "/api/agences/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/agences/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/agences/**").hasRole("ADMIN")

                // Services
                .requestMatchers(HttpMethod.POST, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/services/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")

                // Catégories
                .requestMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")

                // Fournisseurs
                .requestMatchers(HttpMethod.POST, "/api/fournisseurs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/fournisseurs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/fournisseurs/**").hasRole("ADMIN")

                // Immobilisations
                .requestMatchers(HttpMethod.POST, "/api/immobilisations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/immobilisations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/immobilisations/**").hasRole("ADMIN")

                // Mouvements
                .requestMatchers(HttpMethod.POST, "/api/mouvements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/mouvements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/mouvements/**").hasRole("ADMIN")

                // Campagnes inventaire
                .requestMatchers(HttpMethod.POST, "/api/inventaires/campagnes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/inventaires/campagnes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/inventaires/campagnes/**").hasRole("ADMIN")

                // Demandes d'immobilisation : la décision sur une demande, le
                // choix du bien à livrer et la suppression relèvent de l'ADMIN
                // (le dépôt et la lecture restent ouverts, filtrés côté métier
                // sur l'identité de l'appelant).
                // Un `*` simple, pas `**` : PathPattern n'autorise pas de segment
                // après un `**`, et ce matcher est évalué à chaque requête.
                .requestMatchers(HttpMethod.GET, "/api/demandes/candidats").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/demandes/*/decision").hasRole("ADMIN")
                // Aucun endpoint DELETE n'existe : une demande ne se supprime pas,
                // elle s'annule (ANNULEE préserve la trace). La règle reste posée
                // pour que l'ajout accidentel d'un DELETE ne retombe pas sur le
                // repli `.anyRequest().authenticated()`.
                .requestMatchers(HttpMethod.DELETE, "/api/demandes/**").hasRole("ADMIN")

                // Upload photo immobilisation
                .requestMatchers(HttpMethod.POST, "/api/upload/immobilisation-photo").hasRole("ADMIN")

                // Journal d'audit : la trace des actions sensibles et des
                // sessions est réservée à l'ADMIN — elle décrit des actions
                // d'autres utilisateurs.
                .requestMatchers("/api/journal-audit/**").hasRole("ADMIN")

                // ============================================
                // ROUTES AUTHENTIFIÉES (ADMIN + EMPLOYE)
                // ============================================

                // Consultation immobilisations
                .requestMatchers(HttpMethod.GET, "/api/immobilisations/**").authenticated()

                // Consultation mouvements
                .requestMatchers(HttpMethod.GET, "/api/mouvements/**").authenticated()

                // Demandes : dépôt, consultation de ses propres demandes et
                // retrait d'une demande encore en attente (contrôlés en métier).
                .requestMatchers(HttpMethod.GET, "/api/demandes/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/demandes/**").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/demandes/*/annuler").authenticated()

                // Consultation paramètres
                .requestMatchers(HttpMethod.GET, "/api/agences/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/services/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/fournisseurs/**").authenticated()

                // Inventaire — exclusif à l'ADMIN (campagnes rattachées à une
                // agence, pas à un service : aucun filtrage par service possible).
                .requestMatchers("/api/inventaires/**").hasRole("ADMIN")

                // Dashboard
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**").authenticated()

                // Export — les rapports d'inventaire sont exclusifs à l'ADMIN
                .requestMatchers(HttpMethod.GET, "/api/export/inventaires/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/export/**").authenticated()

                // Upload photo de profil
                .requestMatchers(HttpMethod.POST, "/api/upload/photo").authenticated()

                // ============================================
                // TOUT LE RESTE : authentifié
                // ============================================
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sans ceci, Spring Security répond 403 (ForbiddenEntryPoint) aux
            // requêtes non authentifiées : l'intercepteur axios ne se déclenche
            // que sur 401, la session n'était donc jamais purgée automatiquement.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}