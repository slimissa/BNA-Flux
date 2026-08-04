package com.bna.flux.exception;

import com.bna.flux.dto.ReponseErreur;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global d'exceptions pour l'API REST BNA-FLUX.
 * <p>
 * Intercepte toutes les exceptions levées par les contrôleurs et les
 * transforme en réponses d'erreur standardisées au format {@link ReponseErreur}.
 * </p>
 *
 * <p><b>Principes :</b></p>
 * <ul>
 *   <li>Toute réponse d'erreur a une structure cohérente (statut, code, message).</li>
 *   <li>Les messages sont en français et adaptés à l'utilisateur final.</li>
 *   <li>Les détails techniques ne sont jamais exposés en production.</li>
 *   <li>Les erreurs sont logguées avec le niveau approprié (WARN pour métier,
 *       ERROR pour technique).</li>
 * </ul>
 *
 * <p><b>Mapping HTTP :</b></p>
 * <table border="1">
 *   <tr><th>Exception</th><th>HTTP</th><th>Code</th></tr>
 *   <tr><td>RibInvalideException</td><td>400</td><td>RIB_INVALIDE</td></tr>
 *   <tr><td>DeviseInconnueException</td><td>400</td><td>DEVISE_INCONNUE</td></tr>
 *   <tr><td>ExpressionRegleInvalideException</td><td>400</td><td>REGLE_SYNTAXE_INVALIDE</td></tr>
 *   <tr><td>MethodArgumentNotValidException</td><td>400</td><td>VALIDATION_ECHOUEE</td></tr>
 *   <tr><td>ConstraintViolationException</td><td>400</td><td>VALIDATION_ECHOUEE</td></tr>
 *   <tr><td>BadCredentialsException</td><td>401</td><td>AUTHENTIFICATION_ECHOUEE</td></tr>
 *   <tr><td>ExpiredJwtException</td><td>401</td><td>JETON_EXPIRE</td></tr>
 *   <tr><td>JwtException</td><td>401</td><td>JETON_INVALIDE</td></tr>
 *   <tr><td>AccesRefuseException</td><td>403</td><td>ACCES_REFUSE</td></tr>
 *   <tr><td>AccessDeniedException</td><td>403</td><td>ACCES_REFUSE</td></tr>
 *   <tr><td>DisjoncteurOuvertException</td><td>422</td><td>DISJONCTEUR_OUVERT</td></tr>
 *   <tr><td>Exception</td><td>500</td><td>ERREUR_INTERNE</td></tr>
 * </table>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestControllerAdvice
public class GestionnaireGlobalExceptions {

    // Exceptions métier BNA-FLUX

    /**
     * Gère les erreurs de validation de RIB.
     * <p>
     * Levée par {@link com.bna.flux.service.ValidateurRib} lorsque
     * la clé de contrôle ne correspond pas.
     * </p>
     */
    @ExceptionHandler(RibInvalideException.class)
    public ResponseEntity<ReponseErreur> handleRibInvalide(RibInvalideException ex, HttpServletRequest request) {
        log.warn("RIB invalide — {} — {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of("RIB_INVALIDE", ex.getMessage(), ex.getDetails(), request.getRequestURI()));
    }

    /**
     * Gère les erreurs de devise inconnue ou inactive.
     * <p>
     * Levée par {@link com.bna.flux.service.pipeline.etape.EtapeValidation}
     * lorsque le code devise n'existe pas en base.
     * </p>
     */
    @ExceptionHandler(DeviseInconnueException.class)
    public ResponseEntity<ReponseErreur> handleDeviseInconnue(DeviseInconnueException ex, HttpServletRequest request) {
        log.warn("Devise invalide — {} — {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of("DEVISE_INCONNUE", ex.getMessage(), null, request.getRequestURI()));
    }

    /**
     * Gère les erreurs de syntaxe dans les expressions SpEL des règles.
     * <p>
     * Levée par {@link com.bna.flux.service.MoteurRegles} lors de la
     * compilation d'une expression invalide.
     * </p>
     */
    @ExceptionHandler(ExpressionRegleInvalideException.class)
    public ResponseEntity<ReponseErreur> handleExpressionRegleInvalide(
            ExpressionRegleInvalideException ex, HttpServletRequest request) {
        log.warn("Expression de règle invalide — {} — {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of("REGLE_SYNTAXE_INVALIDE", ex.getMessage(), null, request.getRequestURI()));
    }

    /**
     * Gère les ouvertures de disjoncteur.
     * <p>
     * Levée par {@link com.bna.flux.service.ServiceDisjoncteur} lorsque
     * le circuit breaker est OUVERT pour une cible.
     * </p>
     */
    @ExceptionHandler(DisjoncteurOuvertException.class)
    public ResponseEntity<ReponseErreur> handleDisjoncteurOuvert(
            DisjoncteurOuvertException ex, HttpServletRequest request) {
        log.warn("Disjoncteur ouvert — {} — {}", ex.getMessage(), request.getRequestURI());

        Map<String, Object> details = new HashMap<>();
        details.put("typeCible", ex.getTypeCible());
        details.put("identifiantCible", ex.getIdentifiantCible());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ReponseErreur.of("DISJONCTEUR_OUVERT", ex.getMessage(), details, request.getRequestURI()));
    }

    /**
     * Gère les erreurs d'accès refusé personnalisées.
     */
    @ExceptionHandler(AccesRefuseException.class)
    public ResponseEntity<ReponseErreur> handleAccesRefuse(AccesRefuseException ex, HttpServletRequest request) {
        log.warn("Accès refusé — {} — {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ReponseErreur.of("ACCES_REFUSE", ex.getMessage(), null, request.getRequestURI()));
    }

    // Validation des données

    /**
     * Gère les erreurs de validation des DTOs annotés avec {@code @Valid}.
     * <p>
     * Extrait le nom du champ et le message de chaque erreur de validation
     * pour une réponse structurée.
     * </p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ReponseErreur> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.debug("Erreur de validation — {} — {} erreur(s)", request.getRequestURI(), ex.getErrorCount());

        // Extraire les erreurs par champ
        Map<String, String> erreurs = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Valeur invalide",
                        (msg1, msg2) -> msg1 // En cas de duplicate, garder le premier
                ));

        // Construire un message lisible
        String message = "Erreur de validation : " + erreurs.entrySet().stream()
                .map(e -> e.getKey() + " — " + e.getValue())
                .collect(Collectors.joining("; "));

        Map<String, Object> details = new HashMap<>();
        details.put("erreurs", erreurs);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of("VALIDATION_ECHOUEE", message, details, request.getRequestURI()));
    }

    /**
     * Gère les erreurs de validation au niveau des contraintes de bean
     * (hors @Valid sur les DTOs).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ReponseErreur> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.debug("Violation de contrainte — {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of("VALIDATION_ECHOUEE", ex.getMessage(), null, request.getRequestURI()));
    }

    /**
     * Gère les paramètres de requête manquants.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ReponseErreur> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.debug("Paramètre manquant : {}", ex.getParameterName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of(
                        "VALIDATION_ECHOUEE",
                        "Le paramètre '" + ex.getParameterName() + "' est obligatoire.",
                        null,
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les erreurs de conversion de type dans les paramètres.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ReponseErreur> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.debug("Erreur de type : {} — attendu {}", ex.getName(), ex.getRequiredType());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of(
                        "VALIDATION_ECHOUEE",
                        "Le paramètre '" + ex.getName() + "' doit être de type " +
                                (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valide"),
                        null,
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les requêtes avec un corps JSON mal formé.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ReponseErreur> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Corps de requête illisible — {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ReponseErreur.of(
                        "VALIDATION_ECHOUEE",
                        "Le corps de la requête est invalide ou mal formé. Vérifiez la syntaxe JSON.",
                        null,
                        request.getRequestURI()
                ));
    }

    // Authentification et autorisation

    /**
     * Gère les échecs d'authentification (mauvais email/mot de passe).
     * <p>
     * Retourne toujours un message générique pour éviter l'énumération
     * des utilisateurs.
     * </p>
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ReponseErreur> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Échec d'authentification — {} — {}", ex.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ReponseErreur.of(
                        "AUTHENTIFICATION_ECHOUEE",
                        "Email ou mot de passe incorrect.",
                        null,
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les tokens JWT expirés.
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ReponseErreur> handleJwtExpired(
            ExpiredJwtException ex, HttpServletRequest request) {
        log.debug("Token JWT expiré — {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ReponseErreur.of(
                        "JETON_EXPIRE",
                        "Votre session a expiré. Veuillez vous reconnecter.",
                        null,
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les tokens JWT invalides.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ReponseErreur> handleJwtInvalid(
            JwtException ex, HttpServletRequest request) {
        log.debug("Token JWT invalide — {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ReponseErreur.of(
                        "JETON_INVALIDE",
                        "Token d'authentification invalide.",
                        null,
                        request.getRequestURI()
                ));
    }

    /**
     * Gère les accès refusés (Spring Security).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ReponseErreur> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Accès refusé — {} — {}", ex.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ReponseErreur.of(
                        "ACCES_REFUSE",
                        "Vous n'avez pas les droits suffisants pour effectuer cette action.",
                        null,
                        request.getRequestURI()
                ));
    }

    // Ressources non trouvées

    /**
     * Gère les requêtes vers des endpoints inexistants (404 statique).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ReponseErreur> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("Ressource statique non trouvée — {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ReponseErreur.of(
                        "RESSOURCE_INTROUVABLE",
                        "La ressource demandée '" + request.getRequestURI() + "' n'existe pas.",
                        null,
                        request.getRequestURI()
                ));
    }

    // Erreurs base de données

    /**
     * Gère les violations de contrainte d'intégrité (unicité, FK, etc.).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ReponseErreur> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Violation d'intégrité des données — {}", ex.getMessage(), ex);

        String message = "Opération impossible : contrainte d'intégrité violée.";
        String code = "ERREUR_INTEGRITE";

        // Affiner le message selon la contrainte
        String causeMsg = ex.getMostSpecificCause().getMessage();
        if (causeMsg != null) {
            if (causeMsg.contains("UNIQUE") || causeMsg.contains("unique")) {
                message = "Cette valeur existe déjà. Veuillez en choisir une autre.";
                code = "DOUBLON";
            } else if (causeMsg.contains("foreign key") || causeMsg.contains("FOREIGN KEY")) {
                message = "Opération impossible : la ressource référencée n'existe pas.";
                code = "REFERENCE_INTROUVABLE";
            }
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ReponseErreur.of(code, message, null, request.getRequestURI()));
    }

    // Fallback — Toute autre exception non gérée

    /**
     * Gère toutes les exceptions non capturées par les handlers spécifiques.
     * <p>
     * Dernier filet de sécurité. Loggue l'erreur complète en ERROR et retourne
     * un message générique à l'utilisateur.
     * </p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ReponseErreur> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Erreur interne non gérée — {} — {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ReponseErreur.of(
                        "ERREUR_INTERNE",
                        "Une erreur interne est survenue. Veuillez réessayer plus tard ou contacter le support.",
                        null,
                        request.getRequestURI()
                ));
    }
}