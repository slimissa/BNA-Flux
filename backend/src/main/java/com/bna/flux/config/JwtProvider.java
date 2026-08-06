package com.bna.flux.config;

import com.bna.flux.entity.Utilisateur;
import com.bna.flux.repository.UtilisateurRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fournisseur de tokens JWT pour l'authentification et l'autorisation.
 * <p>
 * Responsable de :
 * </p>
 * <ul>
 *   <li>Génération des tokens d'accès (courte durée : 60 minutes)</li>
 *   <li>Génération des tokens de rafraîchissement (longue durée : 24 heures)</li>
 *   <li>Validation et extraction des claims des tokens</li>
 *   <li>Résolution de l'utilisateur à partir d'un token valide</li>
 * </ul>
 *
 * <p><b>Structure du token JWT :</b></p>
 * <pre>
 * Header : { "alg": "HS256", "typ": "JWT" }
 * Payload : {
 *     "sub": "email@bna.com.tn",
 *     "nom": "Ahmed Ben Salah",
 *     "role": "OPERATEUR",
 *     "agence": "601",
 *     "type": "ACCESS",
 *     "iat": 1722776400,
 *     "exp": 1722780000,
 *     "iss": "bna-flux"
 * }
 * </pre>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Algorithme HMAC-SHA256 avec une clé d'au moins 256 bits.</li>
 *   <li>En développement, la clé est dans application-dev.yml.</li>
 *   <li>En production, la clé DOIT être injectée via variable d'environnement.</li>
 *   <li>Le mot de passe n'est JAMAIS inclus dans le payload JWT.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class JwtProvider {

    // Configuration

    @Value("${bna.securite.jwt.secret}")
    private String secret;

    @Getter
    @Value("${bna.securite.jwt.duree-acces-minutes:60}")
    private long dureeAccesMinutes;

    @Getter
    @Value("${bna.securite.jwt.duree-rafraichissement-heures:24}")
    private long dureeRafraichissementHeures;

    @Value("${bna.securite.jwt.emetteur:bna-flux}")
    private String emetteur;

    @Value("${bna.securite.jwt.audience:bna-agences}")
    private String audience;

    private final UtilisateurRepository utilisateurRepository;

    /**
     * Clé secrète dérivée de la propriété de configuration.
     * Initialisée paresseusement pour éviter les erreurs si la propriété
     * est absente lors de l'instanciation.
     */
    private SecretKey secretKey;

    // Constructeur

    public JwtProvider(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    // Génération de tokens

    /**
     * Génère un token d'accès JWT pour un utilisateur authentifié.
     * <p>
     * Durée de validité : configurable (défaut 60 minutes).
     * </p>
     *
     * @param utilisateur l'utilisateur authentifié
     * @return le token JWT signé
     */
    public String genererTokenAcces(Utilisateur utilisateur) {
        Map<String, Object> claims = buildClaims(utilisateur, "ACCESS");
        return buildToken(claims, dureeAccesMinutes);
    }

    /**
     * Génère un token de rafraîchissement JWT.
     * <p>
     * Durée de validité : configurable (défaut 24 heures).
     * Contient moins de claims que le token d'accès pour limiter
     * l'exposition en cas de vol.
     * </p>
     *
     * @param utilisateur l'utilisateur authentifié
     * @return le token de rafraîchissement signé
     */
    public String genererTokenRafraichissement(Utilisateur utilisateur) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", utilisateur.getEmail());
        claims.put("type", "REFRESH");
        return buildToken(claims, dureeRafraichissementHeures * 60);
    }

    /**
     * Construit les claims (payload) du token JWT pour un token d'accès.
     *
     * @param utilisateur l'utilisateur
     * @param typeToken   le type de token (ACCESS ou REFRESH)
     * @return la map des claims
     */
    private Map<String, Object> buildClaims(Utilisateur utilisateur, String typeToken) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", utilisateur.getEmail());
        claims.put("nom", utilisateur.getNom());
        claims.put("role", utilisateur.getRole().name());
        claims.put("agence", utilisateur.getCodeAgence());
        claims.put("type", typeToken);
        return claims;
    }

    /**
     * Construit et signe un token JWT.
     *
     * @param claims       les claims à inclure dans le payload
     * @param dureeMinutes la durée de validité en minutes
     * @return le token JWT signé
     */
    private String buildToken(Map<String, Object> claims, long dureeMinutes) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(dureeMinutes * 60);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer(emetteur)
                .audience().add(audience).and()
                .signWith(getSecretKey())
                .compact();
    }

    // Validation et extraction

    /**
     * Extrait tous les claims d'un token JWT.
     * <p>
     * Lance une exception si le token est invalide, expiré, ou mal formé.
     * </p>
     *
     * @param token le token JWT
     * @return les claims extraits
     * @throws JwtException si le token est invalide
     */
    public Claims extraireClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extrait l'email (subject) d'un token JWT.
     *
     * @param token le token JWT
     * @return l'email de l'utilisateur
     */
    public String extraireEmail(String token) {
        return extraireClaims(token).getSubject();
    }

    /**
     * Extrait le rôle d'un token JWT.
     *
     * @param token le token JWT
     * @return le rôle (OPERATEUR, SUPERVISEUR, ADMIN)
     */
    public String extraireRole(String token) {
        return extraireClaims(token).get("role", String.class);
    }

    /**
     * Extrait le code agence d'un token JWT.
     *
     * @param token le token JWT
     * @return le code agence (peut être null pour ADMIN)
     */
    public String extraireAgence(String token) {
        return extraireClaims(token).get("agence", String.class);
    }

    /**
     * Extrait le type de token (ACCESS ou REFRESH).
     *
     * @param token le token JWT
     * @return le type de token
     */
    public String extraireType(String token) {
        return extraireClaims(token).get("type", String.class);
    }

    /**
     * Vérifie si le token est un token d'accès.
     *
     * @param token le token JWT
     * @return {@code true} si le type est "ACCESS"
     */
    public boolean estTokenAcces(String token) {
        return "ACCESS".equals(extraireType(token));
    }

    /**
     * Vérifie si le token est un token de rafraîchissement.
     *
     * @param token le token JWT
     * @return {@code true} si le type est "REFRESH"
     */
    public boolean estTokenRafraichissement(String token) {
        return "REFRESH".equals(extraireType(token));
    }

    // Validation

    /**
     * Valide un token JWT et retourne l'utilisateur correspondant.
     * <p>
     * Vérifie :
     * </p>
     * <ol>
     *   <li>La signature du token</li>
     *   <li>L'expiration</li>
     *   <li>Que l'utilisateur existe toujours en base</li>
     *   <li>Que l'utilisateur est toujours actif</li>
     * </ol>
     *
     * @param token le token JWT
     * @return un {@link Optional} contenant l'utilisateur si le token est valide
     */
    public Optional<Utilisateur> validerEtExtraireUtilisateur(String token) {
        try {
            Claims claims = extraireClaims(token);
            String email = claims.getSubject();

            Optional<Utilisateur> utilisateurOpt = utilisateurRepository.findByEmailAndActifTrue(email);

            if (utilisateurOpt.isPresent()) {
                // Mettre à jour la date de dernière connexion (asynchrone serait mieux)
                // Mise à jour dernière connexion désactivée (nécessite @Transactional)
                log.debug("Token valide pour l'utilisateur : {}", email);
            } else {
                log.warn("Token valide mais utilisateur introuvable ou inactif : {}", email);
            }

            return utilisateurOpt;

        } catch (ExpiredJwtException e) {
            log.warn("Token JWT expiré : {}", e.getMessage());
            return Optional.empty();
        } catch (UnsupportedJwtException e) {
            log.warn("Token JWT non supporté : {}", e.getMessage());
            return Optional.empty();
        } catch (MalformedJwtException e) {
            log.warn("Token JWT mal formé : {}", e.getMessage());
            return Optional.empty();
        } catch (SignatureException e) {
            log.warn("Signature JWT invalide : {}", e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("Token JWT vide ou null : {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException e) {
            log.error("Erreur de validation JWT : {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vérifie si un token est syntaxiquement valide (sans charger l'utilisateur).
     * <p>
     * Utilisé par le filtre JWT pour décider rapidement si le token
     * doit être rejeté avant même de consulter la base.
     * </p>
     *
     * @param token le token JWT
     * @return {@code true} si le token est syntaxiquement valide et non expiré
     */
    public boolean estSyntaxiquementValide(String token) {
        try {
            extraireClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Calcule la date d'expiration à partir d'un token.
     *
     * @param token le token JWT
     * @return la date d'expiration, ou {@code null} si le token est invalide
     */
    public LocalDateTime getDateExpiration(String token) {
        try {
            Claims claims = extraireClaims(token);
            return claims.getExpiration().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Calcule le temps restant avant expiration du token.
     *
     * @param token le token JWT
     * @return le nombre de minutes restantes, ou -1 si le token est expiré/invalide
     */
    public long getTempsRestantMinutes(String token) {
        try {
            Claims claims = extraireClaims(token);
            Date expiration = claims.getExpiration();
            long millisRestants = expiration.getTime() - System.currentTimeMillis();
            return Math.max(millisRestants / (60 * 1000), -1);
        } catch (JwtException e) {
            return -1;
        }
    }

    // Gestion de la clé secrète

    /**
     * Récupère ou initialise la clé secrète pour la signature JWT.
     * <p>
     * La clé est dérivée de la propriété {@code bna.securite.jwt.secret}.
     * En production, cette valeur DOIT être :
     * </p>
     * <ul>
     *   <li>Injectée via variable d'environnement {@code BNA_JWT_SECRET}</li>
     *   <li>D'au moins 256 bits (32 caractères) pour HMAC-SHA256</li>
     *   <li>Différente entre les environnements (dev, staging, prod)</li>
     *   <li>Stockée dans un vault (HashiCorp Vault, AWS Secrets Manager)</li>
     * </ul>
     *
     * @return la clé secrète
     */
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            if (secret == null || secret.length() < 32) {
                log.error("La clé secrète JWT est trop courte (< 32 caractères). " +
                          "Ceci est acceptable uniquement en développement.");
                // En développement, on complète la clé si nécessaire
                secret = secret != null ? secret.repeat(Math.max(1, 32 / secret.length())) : "default-dev-key-minimum-32-chars!!";
            }
            byte[] keyBytes = Decoders.BASE64.decode(encoderEnBase64(secret));
            secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return secretKey;
    }

    /**
     * Encode une chaîne en Base64 pour l'utiliser comme clé HMAC.
     *
     * @param value la chaîne à encoder
     * @return la représentation Base64
     */
    private String encoderEnBase64(String value) {
        return java.util.Base64.getEncoder().encodeToString(value.getBytes());
    }
}