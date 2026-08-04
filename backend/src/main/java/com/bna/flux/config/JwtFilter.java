package com.bna.flux.config;

import com.bna.flux.entity.Utilisateur;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Filtre HTTP qui intercepte chaque requête pour extraire et valider
 * le token JWT du header {@code Authorization}.
 * <p>
 * Ce filtre s'exécute une fois par requête (via {@link OncePerRequestFilter})
 * et configure le {@link SecurityContextHolder} si le token est valide.
 * </p>
 *
 * <p><b>Fonctionnement :</b></p>
 * <ol>
 *   <li>Extrait le token du header {@code Authorization: Bearer <token>}</li>
 *   <li>Valide le token via {@link JwtProvider}</li>
 *   <li>Si valide, charge l'utilisateur et crée un
 *       {@link UsernamePasswordAuthenticationToken}</li>
 *   <li>Place l'authentification dans le {@link SecurityContextHolder}</li>
 *   <li>Passe la requête au filtre suivant dans la chaîne</li>
 * </ol>
 *
 * <p><b>Endpoints publics (non filtrés) :</b></p>
 * <ul>
 *   <li>{@code POST /api/auth/connexion} — Authentification</li>
 *   <li>{@code GET /api/devises} — Liste des devises (lecture seule)</li>
 *   <li>{@code GET /actuator/health} — Health check</li>
 *   <li>{@code GET /swagger-ui/**} — Documentation API</li>
 *   <li>{@code GET /api-docs/**} — OpenAPI spec</li>
 *   <li>{@code GET /h2-console/**} — Console H2 (développement uniquement)</li>
 * </ul>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Le token n'est jamais loggué (même en DEBUG).</li>
 *   <li>En cas d'erreur, le filtre passe la requête sans authentification
 *       — c'est Spring Security qui rejettera l'accès aux endpoints protégés.</li>
 *   <li>Les rôles sont mappés avec le préfixe {@code ROLE_} requis par Spring Security.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    /**
     * Préfixe du header Authorization pour les tokens Bearer.
     */
    private static final String PREFIXE_BEARER = "Bearer ";

    /**
     * Longueur du préfixe pour l'extraction du token.
     */
    private static final int LONGUEUR_PREFIXE = 7;

    private final JwtProvider jwtProvider;

    public JwtFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    // Filtrage

    /**
     * Point d'entrée du filtre. Appelé pour chaque requête HTTP.
     *
     * @param request     la requête HTTP
     * @param response    la réponse HTTP
     * @param filterChain la chaîne de filtres
     * @throws ServletException en cas d'erreur de servlet
     * @throws IOException      en cas d'erreur d'entrée/sortie
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Extraire le token du header Authorization
        Optional<String> tokenOpt = extraireToken(request);

        if (tokenOpt.isEmpty()) {
            // Pas de token — continuer sans authentification
            log.debug("Aucun token JWT trouvé dans la requête : {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();

        // Valider le token et charger l'utilisateur
        try {
            Optional<Utilisateur> utilisateurOpt = jwtProvider.validerEtExtraireUtilisateur(token);

            if (utilisateurOpt.isPresent()) {
                Utilisateur utilisateur = utilisateurOpt.get();
                configurerContexteSecurite(utilisateur, request);
                log.debug("Authentification JWT réussie pour : {}", utilisateur.getEmail());
            } else {
                log.debug("Token JWT invalide ou utilisateur introuvable");
            }
        } catch (Exception e) {
            // Ne pas bloquer la requête — laisser Spring Security gérer l'accès
            log.debug("Erreur lors de la validation du token JWT : {}", e.getMessage());
        }

        // Continuer la chaîne de filtres
        filterChain.doFilter(request, response);
    }

    // Méthodes privées

    /**
     * Extrait le token JWT du header {@code Authorization}.
     * <p>
     * Format attendu : {@code Authorization: Bearer <token>}
     * </p>
     *
     * @param request la requête HTTP
     * @return un {@link Optional} contenant le token s'il est présent et bien formé
     */
    private Optional<String> extraireToken(HttpServletRequest request) {
        String headerAuth = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(headerAuth)) {
            return Optional.empty();
        }

        if (!headerAuth.startsWith(PREFIXE_BEARER)) {
            log.debug("Header Authorization présent mais ne commence pas par 'Bearer '");
            return Optional.empty();
        }

        String token = headerAuth.substring(LONGUEUR_PREFIXE).trim();

        if (!StringUtils.hasText(token)) {
            log.debug("Header Authorization Bearer présent mais token vide");
            return Optional.empty();
        }

        return Optional.of(token);
    }

    /**
     * Configure le contexte de sécurité Spring avec l'utilisateur authentifié.
     * <p>
     * Crée un {@link UsernamePasswordAuthenticationToken} avec :
     * </p>
     * <ul>
     *   <li>Principal : l'email de l'utilisateur</li>
     *   <li>Credentials : null (déjà authentifié par JWT)</li>
     *   <li>Authorities : le rôle avec préfixe {@code ROLE_}</li>
     * </ul>
     *
     * @param utilisateur l'utilisateur authentifié
     * @param request     la requête HTTP (pour les détails d'authentification)
     */
    private void configurerContexteSecurite(Utilisateur utilisateur, HttpServletRequest request) {
        // Construire l'autorité avec le préfixe ROLE_ requis par Spring Security
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name());

        // Créer le token d'authentification
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        utilisateur.getEmail(),  // principal
                        null,                    // credentials (null = déjà authentifié)
                        Collections.singletonList(authority)
                );

        // Ajouter les détails de la requête (IP, session, etc.)
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Placer l'authentification dans le contexte de sécurité
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // Exclusion de certains endpoints

    /**
     * Détermine si ce filtre doit être ignoré pour une requête donnée.
     * <p>
     * Les endpoints publics (authentification, swagger, health check, etc.)
     * ne nécessitent pas de validation JWT.
     * </p>
     *
     * @param request la requête HTTP
     * @return {@code true} si le filtre doit être ignoré
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        // Endpoints publics — pas de validation JWT
        return path.startsWith("/api/auth/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/actuator/health") ||
               (path.startsWith("/api/devises") && "GET".equalsIgnoreCase(method)) ||
               path.startsWith("/h2-console");
    }
}