package com.bna.flux.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration CORS (Cross-Origin Resource Sharing) pour BNA-FLUX.
 * <p>
 * Cette configuration est complémentaire à celle définie dans
 * {@link SecurityConfig}. Elle fournit un {@link CorsFilter}
 * supplémentaire qui agit avant la chaîne de filtres Spring Security,
 * garantissant que les requêtes preflight OPTIONS sont traitées
 * correctement même sans authentification.
 * </p>
 *
 * <p><b>Pourquoi deux configurations CORS ?</b></p>
 * <ul>
 *   <li>{@link SecurityConfig} gère CORS dans le contexte Spring Security
 *       (après les filtres de sécurité).</li>
 *   <li>Ce {@link CorsFilter} global agit avant Spring Security, assurant
 *       que les requêtes OPTIONS (preflight) sont toujours répondues,
 *       même si le token JWT est absent ou invalide.</li>
 * </ul>
 *
 * <p><b>Environnements :</b></p>
 * <ul>
 *   <li><b>Développement</b> — Autorise localhost:4200 (Angular) et localhost:8080.</li>
 *   <li><b>Production</b> — Limité aux origines explicitement configurées
 *       dans les variables d'environnement.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Configuration
public class CorsConfig {

    /**
     * Liste des origines autorisées pour les requêtes CORS.
     * <p>
     * Injectée depuis application.yml. En développement :
     * {@code http://localhost:4200, http://localhost:8080}.
     * </p>
     */
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    /**
     * Liste des méthodes HTTP autorisées.
     */
    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private List<String> allowedMethods;

    /**
     * Liste des headers autorisés dans les requêtes.
     */
    @Value("${app.cors.allowed-headers:Authorization,Content-Type,Accept,Origin,X-Requested-With}")
    private List<String> allowedHeaders;

    /**
     * Durée maximale de mise en cache des réponses preflight (secondes).
     */
    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    /**
     * Indique si les credentials (cookies, authorization headers) sont autorisés.
     */
    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    // Filtre CORS global

    /**
     * Crée un {@link CorsFilter} global qui s'applique avant la chaîne
     * de sécurité Spring.
     * <p>
     * Ce filtre garantit que :
     * </p>
     * <ul>
     *   <li>Les requêtes OPTIONS (preflight) reçoivent une réponse 200 OK
     *       avec les headers CORS appropriés.</li>
     *   <li>Les requêtes cross-origin depuis le frontend Angular
     *       sont acceptées.</li>
     *   <li>Les headers Authorization sont exposés au client.</li>
     * </ul>
     *
     * @return le filtre CORS configuré
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origines autorisées
        configuration.setAllowedOrigins(allowedOrigins);
        log.info("CORS Filter — Origines autorisées : {}", allowedOrigins);

        // Méthodes HTTP autorisées
        configuration.setAllowedMethods(allowedMethods);
        log.debug("CORS Filter — Méthodes autorisées : {}", allowedMethods);

        // Headers autorisés dans les requêtes
        configuration.setAllowedHeaders(allowedHeaders);
        log.debug("CORS Filter — Headers autorisés : {}", allowedHeaders);

        // Headers exposés dans les réponses
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "X-Total-Count",
                "X-Transaction-Id",
                "Link",
                "Location"
        ));

        // Durée de cache des preflight
        configuration.setMaxAge(maxAge);

        // Autoriser les credentials
        configuration.setAllowCredentials(allowCredentials);

        // Appliquer cette configuration à tous les chemins
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("Filtre CORS global initialisé — maxAge={}s, credentials={}",
                maxAge, allowCredentials);

        return new CorsFilter(source);
    }

    // Configuration CORS spécifique pour l'API publique

    /**
     * Crée une configuration CORS permissive pour les endpoints publics
     * (devises, authentification, swagger).
     * <p>
     * Cette configuration est utilisée par les contrôleurs publics
     * via {@code @CrossOrigin} ou peut être appliquée globalement.
     * </p>
     *
     * @return la configuration CORS pour les endpoints publics
     */
    @Bean
    public CorsConfiguration corsConfigurationPublique() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Pour les endpoints publics, on peut être plus permissif sur les origines
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Accept", "Origin"));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(false); // Pas de credentials pour les endpoints publics

        return configuration;
    }
}