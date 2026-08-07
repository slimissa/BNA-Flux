package com.bna.flux.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration centrale de Spring Security pour BNA-FLUX.
 * <p>
 * Définit la chaîne de filtres de sécurité, les règles d'accès par rôle,
 * la configuration CORS, l'encodeur de mots de passe, et l'intégration
 * du filtre JWT.
 * </p>
 *
 * <p><b>Principes de sécurité :</b></p>
 * <ul>
 *   <li><b>Stateless</b> — Pas de session HTTP (JWT uniquement).</li>
 *   <li><b>CSRF désactivé</b> — Non nécessaire pour une API REST stateless.</li>
 *   <li><b>CORS configuré</b> — Permet les requêtes depuis le frontend Angular.</li>
 *   <li><b>BCrypt</b> — Hashage des mots de passe avec un facteur de coût 12.</li>
 *   <li><b>Method Security</b> — Autorisations fines via {@code @PreAuthorize}
 *       dans les contrôleurs.</li>
 * </ul>
 *
 * <p><b>Règles d'accès :</b></p>
 * <table border="1">
 *   <tr><th>Endpoint</th><th>Méthode</th><th>Accès</th></tr>
 *   <tr><td>/api/auth/**</td><td>POST</td><td>Public</td></tr>
 *   <tr><td>/api/devises/**</td><td>GET</td><td>Public</td></tr>
 *   <tr><td>/actuator/health</td><td>GET</td><td>Public</td></tr>
 *   <tr><td>/swagger-ui/**</td><td>GET</td><td>Public</td></tr>
 *   <tr><td>/api-docs/**</td><td>GET</td><td>Public</td></tr>
 *   <tr><td>/h2-console/**</td><td>*</td><td>Public (dev only)</td></tr>
 *   <tr><td>/ws/**</td><td>*</td><td>Public (WebSocket upgrade)</td></tr>
 *   <tr><td>/api/transactions/**</td><td>*</td><td>OPERATEUR, SUPERVISEUR, ADMIN</td></tr>
 *   <tr><td>/api/regles/**</td><td>GET</td><td>OPERATEUR, SUPERVISEUR, ADMIN</td></tr>
 *   <tr><td>/api/regles/**</td><td>POST, PUT, DELETE</td><td>SUPERVISEUR, ADMIN</td></tr>
 *   <tr><td>/api/disjoncteurs/**</td><td>PUT (reset)</td><td>SUPERVISEUR, ADMIN</td></tr>
 *   <tr><td>/api/utilisateurs/**</td><td>*</td><td>ADMIN</td></tr>
 * </table>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    /**
     * Liste des origines autorisées pour les requêtes CORS.
     * <p>
     * Injectée depuis application.yml (variable {@code app.cors.allowed-origins}).
     * En développement : localhost:4200, localhost:8080.
     * En production : URL du frontend déployé.
     * </p>
     */
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    /**
     * Durée maximale de mise en cache des réponses CORS preflight (en secondes).
     */
    @Value("${app.cors.max-age:3600}")
    private long corsMaxAge;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    // Chaîne de filtres de sécurité

    /**
     * Configure la chaîne de filtres Spring Security.
     * <p>
     * Ordre des filtres :
     * </p>
     * <ol>
     *   <li>CORS — Traitement des requêtes cross-origin</li>
     *   <li>CSRF — Désactivé (API stateless)</li>
     *   <li>JwtFilter — Authentification par token</li>
     *   <li>Authorization — Vérification des rôles</li>
     * </ol>
     *
     * @param http la configuration HTTP Security
     * @return la chaîne de filtres configurée
     * @throws Exception en cas d'erreur de configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // CSRF — Désactivé (API REST stateless avec JWT)
            .csrf(AbstractHttpConfigurer::disable)

            // Gestion des sessions — Stateless
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Règles d'autorisation
            .authorizeHttpRequests(auth -> auth
                // --- Endpoints publics ---
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/devises/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                .requestMatchers("/api-docs/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/ws/**").permitAll()

                // --- Endpoints protégés — Tous rôles ---
                .requestMatchers(HttpMethod.GET, "/api/transactions/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/alertes/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/tableau-bord/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/regles/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/disjoncteurs/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")

                // --- Endpoints protégés — SUPERVISEUR et ADMIN ---
                .requestMatchers(HttpMethod.POST, "/api/transactions/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/alertes/**").hasAnyRole("OPERATEUR", "SUPERVISEUR", "ADMIN")
                .requestMatchers("/api/regles/**").hasAnyRole("SUPERVISEUR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/disjoncteurs/**").hasAnyRole("SUPERVISEUR", "ADMIN")

                // --- Endpoints protégés — ADMIN uniquement ---
                .requestMatchers("/api/utilisateurs/**").hasRole("ADMIN")

                // --- Tout le reste nécessite authentification ---
                .anyRequest().authenticated()
            )

            // Formulaire de login — Désactivé (JWT)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // Ajout du filtre JWT avant UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

            // Headers de sécurité
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // Pour H2 Console
                .xssProtection(xss -> xss.disable())       // Géré par le frontend
            );

        log.info("Chaîne de sécurité Spring Security configurée — Mode stateless JWT");
        return http.build();
    }

    // Configuration CORS

    /**
     * Source de configuration CORS pour les requêtes cross-origin.
     * <p>
     * En développement, autorise localhost:4200 (Angular) et localhost:8080.
     * En production, seules les origines explicitement listées sont autorisées.
     * </p>
     *
     * @return la source de configuration CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origines autorisées
        configuration.setAllowedOrigins(allowedOrigins);
        log.info("CORS configuré pour les origines : {}", allowedOrigins);

        // Méthodes HTTP autorisées
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Headers autorisés
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "If-Modified-Since",
                "If-None-Match"
        ));

        // Headers exposés au client
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "X-Total-Count",
                "Link"
        ));

        // Durée de cache des preflight requests
        configuration.setMaxAge(corsMaxAge);

        // Autoriser les credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // Beans de sécurité

    /**
     * Fournit l'encodeur de mots de passe BCrypt.
     * <p>
     * Facteur de coût : 12 (bon équilibre sécurité/performance).
     * Les mots de passe sont automatiquement salés par BCrypt.
     * </p>
     *
     * @return l'encodeur BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Fournit le gestionnaire d'authentification.
     * <p>
     * Utilisé par {@code AuthController} pour authentifier les utilisateurs
     * avec email/mot de passe.
     * </p>
     *
     * @param authConfiguration la configuration d'authentification
     * @return le gestionnaire d'authentification
     * @throws Exception en cas d'erreur de configuration
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfiguration) throws Exception {
        return authConfiguration.getAuthenticationManager();
    }
}