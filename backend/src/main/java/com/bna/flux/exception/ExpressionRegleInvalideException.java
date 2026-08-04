package com.bna.flux.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception levée lorsqu'une expression SpEL de règle est syntaxiquement invalide.
 * <p>
 * La validation syntaxique est effectuée par {@link com.bna.flux.service.MoteurRegles}
 * lors de la création ou modification d'une règle. L'expression est compilée via
 * le {@link org.springframework.expression.spel.standard.SpelExpressionParser}
 * configuré dans {@link com.bna.flux.config.SpelConfig}.
 * </p>
 *
 * <p><b>Erreurs de syntaxe courantes :</b></p>
 * <ul>
 *   <li>Opérateur inconnu ou mal orthographié</li>
 *   <li>Parenthèses non équilibrées</li>
 *   <li>Variable inexistante (typo dans le nom de variable)</li>
 *   <li>Type incompatible (comparaison String avec Integer)</li>
 *   <li>Guillemets manquants autour des chaînes de caractères</li>
 *   <li>Utilisation de méthodes non autorisées (sandbox SpEL)</li>
 * </ul>
 *
 * <p><b>Variables disponibles dans les expressions :</b></p>
 * <ul>
 *   <li>{@code montant} — BigDecimal</li>
 *   <li>{@code codeDevise} — String (3 lettres)</li>
 *   <li>{@code typeTransaction} — String (VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT)</li>
 *   <li>{@code canal} — String (AGENCE, DAB, EN_LIGNE, MOBILE)</li>
 *   <li>{@code paysOrigine} — String ou null</li>
 *   <li>{@code categorieContrepartie} — String ou null</li>
 *   <li>{@code ribSource} — String (20 chiffres)</li>
 *   <li>{@code ribDestination} — String (20 chiffres)</li>
 * </ul>
 *
 * <p>Cette exception est interceptée par {@link GestionnaireGlobalExceptions}
 * et retourne une réponse HTTP 400 (BAD_REQUEST) avec le code
 * {@code REGLE_SYNTAXE_INVALIDE}.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Getter
public class ExpressionRegleInvalideException extends RuntimeException {

    /**
     * L'expression SpEL qui a causé l'erreur.
     */
    private final String expression;

    /**
     * Message d'erreur technique du parser SpEL.
     */
    private final String erreurParser;

    /**
     * Position approximative de l'erreur dans l'expression.
     */
    private final Integer positionErreur;

    /**
     * Crée une exception avec l'expression et le message d'erreur du parser.
     *
     * @param expression   l'expression SpEL invalide
     * @param erreurParser le message d'erreur retourné par le parser SpEL
     */
    public ExpressionRegleInvalideException(String expression, String erreurParser) {
        super("L'expression de règle est syntaxiquement invalide : " + erreurParser +
              (expression != null ? "\nExpression : " + expression : ""));
        this.expression = expression;
        this.erreurParser = erreurParser;
        this.positionErreur = null;
    }

    /**
     * Crée une exception avec l'expression, le message d'erreur et la position.
     *
     * @param expression      l'expression SpEL invalide
     * @param erreurParser    le message d'erreur du parser
     * @param positionErreur  la position approximative de l'erreur
     */
    public ExpressionRegleInvalideException(String expression, String erreurParser, Integer positionErreur) {
        super("L'expression de règle contient une erreur à la position " + positionErreur + " : " + erreurParser +
              (expression != null ? "\nExpression : " + expression : ""));
        this.expression = expression;
        this.erreurParser = erreurParser;
        this.positionErreur = positionErreur;
    }

    /**
     * Crée une exception pour une expression avec des variables inconnues.
     *
     * @param expression        l'expression invalide
     * @param variablesInconnues liste des variables non reconnues
     * @return l'exception
     */
    public static ExpressionRegleInvalideException variablesInconnues(String expression, String variablesInconnues) {
        return new ExpressionRegleInvalideException(
                expression,
                "Variables inconnues : " + variablesInconnues +
                ". Variables autorisées : montant, codeDevise, typeTransaction, canal, " +
                "paysOrigine, categorieContrepartie, ribSource, ribDestination."
        );
    }

    /**
     * Crée une exception pour un type d'opération non supporté.
     *
     * @param expression l'expression invalide
     * @param operation  l'opération non supportée
     * @return l'exception
     */
    public static ExpressionRegleInvalideException operationNonSupportee(String expression, String operation) {
        return new ExpressionRegleInvalideException(
                expression,
                "L'opération '" + operation + "' n'est pas supportée. " +
                "Opérations autorisées : comparaisons (==, !=, <, >, <=, >=), " +
                "logique (AND, OR, NOT), appartenance (IN), pattern matching (matches)."
        );
    }

    /**
     * Récupère les détails structurés de l'erreur pour la réponse API.
     *
     * @return une map contenant l'expression et les détails de l'erreur
     */
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("expression", expression);
        details.put("erreurParser", erreurParser);
        if (positionErreur != null) {
            details.put("positionErreur", positionErreur);
        }
        return details;
    }
}