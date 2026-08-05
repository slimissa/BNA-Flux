package com.bna.flux.controller;

import com.bna.flux.config.JwtProvider;
import com.bna.flux.dto.ReponseConnexion;
import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.dto.RequeteConnexion;
import com.bna.flux.entity.Utilisateur;
import com.bna.flux.repository.UtilisateurRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur REST pour l'authentification des utilisateurs.
 * <p>
 * Point d'entrée pour la connexion (login) et le rafraîchissement
 * des tokens JWT. L'authentification se fait par email et mot de passe.
 * En cas de succès, deux tokens sont générés : un token d'accès (60 min)
 * et un token de rafraîchissement (24h).
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code POST /api/auth/connexion} — Authentification par email/mot de passe</li>
 *   <li>{@code POST /api/auth/rafraichir} — Rafraîchissement du token d'accès</li>
 *   <li>{@code POST /api/auth/deconnexion} — Déconnexion (invalidation côté client)</li>
 * </ul>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Les mots de passe sont hashés avec BCrypt (facteur 12).</li>
 *   <li>En cas d'échec, un message générique est retourné
 *       ("Email ou mot de passe incorrect") sans distinguer si l'email
 *       existe ou non (protection contre l'énumération).</li>
 *   <li>Les tokens sont signés avec HMAC-SHA256.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Endpoints d'authentification JWT")
public class AuthController {

    private final UtilisateurRepository utilisateurRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param utilisateurRepository le repository des utilisateurs
     * @param jwtProvider           le fournisseur de tokens JWT
     * @param passwordEncoder       l'encodeur de mots de passe BCrypt
     */
    public AuthController(UtilisateurRepository utilisateurRepository,
                          JwtProvider jwtProvider,
                          PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    // POST /api/auth/connexion

    /**
     * Authentifie un utilisateur avec son email et mot de passe.
     * <p>
     * En cas de succès, génère un token d'accès et un token de rafraîchissement.
     * Le token d'accès doit être inclus dans le header {@code Authorization: Bearer <token>}
     * pour toutes les requêtes protégées.
     * </p>
     *
     * @param requete le DTO contenant l'email et le mot de passe
     * @return la réponse contenant les tokens JWT et les infos utilisateur
     */
    @PostMapping("/connexion")
    @Operation(
            summary = "Authentifier un utilisateur",
            description = "Authentifie un utilisateur avec email/mot de passe et retourne les tokens JWT."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentification réussie",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReponseConnexion.class))),
            @ApiResponse(responseCode = "401", description = "Email ou mot de passe incorrect",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    })
    public ResponseEntity<?> connexion(@Valid @RequestBody RequeteConnexion requete) {
        log.info("Tentative de connexion pour : {}", requete.getEmail());

        try {
            // 1. Rechercher l'utilisateur par email
            Optional<Utilisateur> utilisateurOpt = utilisateurRepository
                    .findByEmail(requete.getEmail().toLowerCase().trim());

            if (utilisateurOpt.isEmpty()) {
                log.warn("Échec de connexion — email introuvable : {}", requete.getEmail());
                throw new BadCredentialsException("Email ou mot de passe incorrect");
            }

            Utilisateur utilisateur = utilisateurOpt.get();

            // 2. Vérifier que l'utilisateur est actif
            if (!utilisateur.isActif()) {
                log.warn("Échec de connexion — compte inactif : {}", requete.getEmail());
                throw new BadCredentialsException("Compte désactivé. Veuillez contacter un administrateur.");
            }

            // 3. Vérifier le mot de passe
            if (!passwordEncoder.matches(requete.getMotDePasse(), utilisateur.getMotDePasse())) {
                log.warn("Échec de connexion — mot de passe incorrect : {}", requete.getEmail());
                throw new BadCredentialsException("Email ou mot de passe incorrect");
            }

            // 4. Générer les tokens
            String tokenAcces = jwtProvider.genererTokenAcces(utilisateur);
            String tokenRafraichissement = jwtProvider.genererTokenRafraichissement(utilisateur);

            // 5. Construire la réponse
            ReponseConnexion reponse = ReponseConnexion.of(
                    tokenAcces,
                    tokenRafraichissement,
                    jwtProvider.getDureeAccesMinutes() * 60,
                    utilisateur.getEmail(),
                    utilisateur.getNom(),
                    utilisateur.getRole().name(),
                    utilisateur.getCodeAgence()
            );

            log.info("Connexion réussie pour : {} (rôle: {}, agence: {})",
                    utilisateur.getEmail(), utilisateur.getRole().name(), utilisateur.getCodeAgence());

            return ResponseEntity.ok(reponse);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ReponseErreur.of("AUTHENTIFICATION_ECHOUEE", e.getMessage()));
        }
    }

    // POST /api/auth/rafraichir

    /**
     * Rafraîchit le token d'accès en utilisant le token de rafraîchissement.
     * <p>
     * Le token de rafraîchissement doit être envoyé dans le header
     * {@code Authorization: Bearer <refresh_token>}.
     * </p>
     *
     * @param headerAuth le header Authorization contenant le refresh token
     * @return un nouveau token d'accès
     */
    @PostMapping("/rafraichir")
    @Operation(
            summary = "Rafraîchir le token d'accès",
            description = "Génère un nouveau token d'accès à partir du token de rafraîchissement."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token rafraîchi avec succès"),
            @ApiResponse(responseCode = "401", description = "Token de rafraîchissement invalide ou expiré",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    })
    public ResponseEntity<?> rafraichir(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Header Authorization: Bearer <refresh_token>")
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String headerAuth) {

        log.debug("Tentative de rafraîchissement de token");

        // Extraire le token du header
        if (headerAuth == null || !headerAuth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ReponseErreur.of("JETON_INVALIDE",
                            "Token de rafraîchissement manquant ou mal formé."));
        }

        String refreshToken = headerAuth.substring(7).trim();

        // Vérifier que c'est bien un token de rafraîchissement
        if (!jwtProvider.estSyntaxiquementValide(refreshToken)) {
            log.warn("Token de rafraîchissement syntaxiquement invalide");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ReponseErreur.of("JETON_INVALIDE",
                            "Token de rafraîchissement invalide ou expiré."));
        }

        if (!jwtProvider.estTokenRafraichissement(refreshToken)) {
            log.warn("Tentative de rafraîchissement avec un token d'accès");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ReponseErreur.of("JETON_INVALIDE",
                            "Le token fourni n'est pas un token de rafraîchissement."));
        }

        // Valider et extraire l'utilisateur
        Optional<Utilisateur> utilisateurOpt = jwtProvider.validerEtExtraireUtilisateur(refreshToken);

        if (utilisateurOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ReponseErreur.of("JETON_EXPIRE",
                            "Token de rafraîchissement expiré. Veuillez vous reconnecter."));
        }

        // Générer un nouveau token d'accès
        Utilisateur utilisateur = utilisateurOpt.get();
        String nouveauToken = jwtProvider.genererTokenAcces(utilisateur);

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("tokenAcces", nouveauToken);
        reponse.put("typeToken", "Bearer");
        reponse.put("expireDans", jwtProvider.getDureeAccesMinutes() * 60);

        log.info("Token rafraîchi avec succès pour : {}", utilisateur.getEmail());
        return ResponseEntity.ok(reponse);
    }

    // POST /api/auth/deconnexion

    /**
     * Déconnexion de l'utilisateur.
     * <p>
     * Dans une architecture JWT stateless, la déconnexion est gérée côté client
     * (suppression du token). Le serveur ne maintient pas de session.
     * Cette méthode existe pour la complétude de l'API et pour journaliser
     * les déconnexions.
     * </p>
     *
     * @return une confirmation de déconnexion
     */
    @PostMapping("/deconnexion")
    @Operation(
            summary = "Se déconnecter",
            description = "Déconnexion de l'utilisateur. Le token doit être supprimé côté client."
    )
    public ResponseEntity<Map<String, Object>> deconnexion() {
        log.debug("Déconnexion demandée");

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("message", "Déconnecté avec succès. Veuillez supprimer le token côté client.");

        return ResponseEntity.ok(reponse);
    }
}