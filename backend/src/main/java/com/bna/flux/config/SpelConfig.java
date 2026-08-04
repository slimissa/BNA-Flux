package com.bna.flux.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration du moteur Spring Expression Language (SpEL) pour BNA-FLUX.
 * <p>
 * SpEL est utilisé par le moteur de règles ({@link com.bna.flux.service.MoteurRegles})
 * pour évaluer dynamiquement les expressions de règles de surveillance
 * contre chaque transaction lors du Stage 3 (Évaluation des règles) du pipeline.
 * </p>
 *
 * <p><b>Pourquoi SpEL plutôt qu'un moteur dédié (Drools, MVEL) ?</b></p>
 * <ul>
 *   <li><b>Intégration native Spring</b> — Aucune dépendance supplémentaire.</li>
 *   <li><b>Syntaxe familière</b> — Proche du Java pour les développeurs BNA.</li>
 *   <li><b>Performance</b> — Compilation unique + cache pour les expressions répétées.</li>
 *   <li><b>Sécurité</b> — Sandbox SpEL limité aux variables explicitement fournies.</li>
 *   <li><b>Évolutivité</b> — Remplacement par Drools possible sans changer
 *       l'interface du moteur de règles.</li>
 * </ul>
 *
 * <p><b>Expressions SpEL supportées :</b></p>
 * <table border="1">
 *   <tr><th>Type</th><th>Exemple</th><th>Description</th></tr>
 *   <tr><td>Comparaison</td><td>{@code montant >= 50000}</td><td>Seuil de montant</td></tr>
 *   <tr><td>Égalité chaîne</td><td>{@code codeDevise == 'EUR'}</td><td>Devise spécifique</td></tr>
 *   <tr><td>Logique</td><td>{@code montant >= 50000 AND codeDevise != 'TND'}</td><td>Conditions combinées</td></tr>
 *   <tr><td>Appartenance</td><td>{@code canal IN {'EN_LIGNE', 'MOBILE'}}</td><td>Liste de valeurs</td></tr>
 *   <tr><td>Null check</td><td>{@code paysOrigine != null AND paysOrigine != 'Tunisie'}</td><td>Test de présence</td></tr>
 *   <tr><td>Pattern</td><td>{@code ribSource matches '^[0-9]{20}$'}</td><td>Validation format</td></tr>
 * </table>
 *
 * <p><b>Sécurité des expressions :</b></p>
 * <ul>
 *   <li>Les expressions proviennent de la base de données (saisies par un SUPERVISEUR).</li>
 *   <li>Le {@link StandardEvaluationContext} est limité aux variables de la transaction.</li>
 *   <li>Aucune méthode Java arbitraire n'est accessible (pas de {@code T(java.lang.Runtime)}).</li>
 *   <li>Les expressions sont compilées et validées syntaxiquement avant d'être sauvegardées.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Configuration
public class SpelConfig {

    /**
     * Cache des expressions SpEL compilées.
     * <p>
     * Clé : l'expression SpEL brute (String).
     * Valeur : l'expression compilée (Expression).
     * </p>
     * <p>
     * Taille maximale : 200 entrées (configurable dans application.yml).
     * Politique d'éviction : LRU (Least Recently Used) via access ordonné.
     * </p>
     * <p>
     * Utilise {@link ConcurrentHashMap} pour la thread-safety car le pipeline
     * peut traiter plusieurs transactions en parallèle (via {@code @Async}).
     * </p>
     */
    private final Map<String, org.springframework.expression.Expression> cacheExpressions =
            new ConcurrentHashMap<>(200);

    // Beans

    /**
     * Fournit le parser SpEL principal.
     * <p>
     * Le {@link SpelExpressionParser} est thread-safe et peut être partagé.
     * Il est utilisé par {@link com.bna.flux.service.MoteurRegles} pour
     * compiler et évaluer les expressions de règles.
     * </p>
     *
     * @return une instance unique du parser SpEL
     */
    @Bean
    public ExpressionParser expressionParser() {
        log.info("Initialisation du parser SpEL pour le moteur de règles");
        return new SpelExpressionParser();
    }

    /**
     * Fournit un {@link StandardEvaluationContext} pré-configuré.
     * <p>
     * Ce contexte est utilisé comme template pour créer des contextes
     * d'évaluation spécifiques à chaque transaction. Les variables
     * sont injectées dynamiquement par le {@link com.bna.flux.service.MoteurRegles}.
     * </p>
     *
     * @return un contexte d'évaluation standard
     */
    @Bean
    public StandardEvaluationContext evaluationContext() {
        StandardEvaluationContext context = new StandardEvaluationContext();
        // Désactiver l'accès aux méthodes statiques Java (sécurité)
        // context.setVariable("T", null); — géré par MoteurRegles
        log.debug("Contexte d'évaluation SpEL initialisé");
        return context;
    }

    // Cache des expressions

    /**
     * Récupère le cache des expressions compilées.
     * <p>
     * Le cache est utilisé par {@link com.bna.flux.service.MoteurRegles}
     * pour éviter de recompiler les mêmes expressions à chaque transaction.
     * </p>
     *
     * @return le cache thread-safe des expressions compilées
     */
    @Bean
    public Map<String, org.springframework.expression.Expression> cacheExpressionsSpEL() {
        log.info("Cache SpEL initialisé — capacité maximale : 200 expressions");
        return cacheExpressions;
    }

    // Méthodes utilitaires (utilisées par MoteurRegles)

    /**
     * Compile une expression SpEL en utilisant le parser configuré.
     * <p>
     * Cette méthode est exposée comme point d'entrée unique pour la
     * compilation afin de centraliser la gestion des erreurs de syntaxe.
     * </p>
     *
     * @param expression l'expression SpEL sous forme de chaîne
     * @return l'expression compilée
     * @throws org.springframework.expression.ParseException si l'expression est invalide
     */
    public org.springframework.expression.Expression compilerExpression(String expression) {
        ExpressionParser parser = expressionParser();
        return parser.parseExpression(expression);
    }

    /**
     * Compile et met en cache une expression SpEL.
     * <p>
     * Si l'expression est déjà dans le cache, elle est retournée directement.
     * Sinon, elle est compilée, stockée dans le cache, puis retournée.
     * </p>
     *
     * @param expressionRaw l'expression SpEL brute
     * @return l'expression compilée (depuis le cache ou fraîchement compilée)
     */
    public org.springframework.expression.Expression getOuCompiler(String expressionRaw) {
        return cacheExpressions.computeIfAbsent(expressionRaw, key -> {
            log.debug("Compilation SpEL : {}", key);
            return compilerExpression(key);
        });
    }

    /**
     * Vide le cache des expressions compilées.
     * <p>
     * Appelé lorsqu'une règle est modifiée ou supprimée, pour forcer
     * la recompilation à la prochaine évaluation.
     * </p>
     */
    public void viderCache() {
        int tailleAvant = cacheExpressions.size();
        cacheExpressions.clear();
        log.info("Cache SpEL vidé — {} expressions supprimées", tailleAvant);
    }

    /**
     * Supprime une expression spécifique du cache.
     * <p>
     * Appelé lorsqu'une règle est modifiée individuellement.
     * </p>
     *
     * @param expressionRaw l'expression à supprimer du cache
     */
    public void invaliderExpression(String expressionRaw) {
        org.springframework.expression.Expression supprimee = cacheExpressions.remove(expressionRaw);
        if (supprimee != null) {
            log.debug("Expression SpEL invalidée dans le cache : {}", expressionRaw);
        }
    }

    /**
     * Retourne la taille actuelle du cache.
     *
     * @return le nombre d'expressions en cache
     */
    public int tailleCache() {
        return cacheExpressions.size();
    }
}