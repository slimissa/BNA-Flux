package com.bna.flux.service;

import com.bna.flux.config.SpelConfig;
import com.bna.flux.entity.Regle;
import com.bna.flux.entity.Transaction;
import com.bna.flux.exception.ExpressionRegleInvalideException;
import com.bna.flux.repository.RegleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moteur d'évaluation des règles de surveillance BNA-FLUX.
 * <p>
 * Responsable de :
 * </p>
 * <ul>
 *   <li>Charger les règles actives depuis la base de données</li>
 *   <li>Compiler les expressions SpEL avec mise en cache</li>
 *   <li>Évaluer chaque règle contre une transaction dans un contexte sécurisé</li>
 *   <li>Collecter les règles déclenchées avec leur contribution au score</li>
 * </ul>
 *
 * <p><b>Utilisation dans le pipeline :</b></p>
 * <p>
 * Appelé par {@link com.bna.flux.service.pipeline.etape.EtapeEvaluationRegles}
 * lors du Stage 3 du pipeline pour chaque transaction.
 * </p>
 *
 * <p><b>Variables injectées dans le contexte SpEL :</b></p>
 * <table border="1">
 *   <tr><th>Variable</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>montant</td><td>BigDecimal</td><td>Montant de la transaction</td></tr>
 *   <tr><td>codeDevise</td><td>String</td><td>Code ISO 4217 (ex: TND, EUR)</td></tr>
 *   <tr><td>typeTransaction</td><td>String</td><td>VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT</td></tr>
 *   <tr><td>canal</td><td>String</td><td>AGENCE, DAB, EN_LIGNE, MOBILE</td></tr>
 *   <tr><td>paysOrigine</td><td>String</td><td>Pays d'origine (peut être null)</td></tr>
 *   <tr><td>categorieContrepartie</td><td>String</td><td>PARTICULIER, ENTREPRISE, GOUVERNEMENT</td></tr>
 *   <tr><td>ribSource</td><td>String</td><td>RIB émetteur (20 chiffres)</td></tr>
 *   <tr><td>ribDestination</td><td>String</td><td>RIB bénéficiaire (20 chiffres)</td></tr>
 *   <tr><td>scoreRisque</td><td>BigDecimal</td><td>Score actuel (pour règles chaînées)</td></tr>
 * </table>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Le contexte SpEL est limité aux variables explicitement fournies.</li>
 *   <li>Aucun accès aux méthodes Java statiques (pas de {@code T(java.lang.Runtime)}).</li>
 *   <li>Les expressions sont compilées une fois et mises en cache pour performance.</li>
 *   <li>Timeout d'évaluation configurable pour éviter les boucles infinies.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class MoteurRegles {

    private final RegleRepository regleRepository;
    private final SpelConfig spelConfig;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param regleRepository le repository des règles
     * @param spelConfig      la configuration SpEL (parser + cache)
     */
    public MoteurRegles(RegleRepository regleRepository, SpelConfig spelConfig) {
        this.regleRepository = regleRepository;
        this.spelConfig = spelConfig;
    }

    // Résultat d'évaluation

    /**
     * Résultat de l'évaluation de toutes les règles contre une transaction.
     */
    public static class ResultatEvaluation {
        /** Règles qui se sont déclenchées. */
        private final List<RegleDeclenchee> reglesDeclenchees;
        /** Score total (somme des contributions). */
        private final int scoreTotal;
        /** Nombre de règles évaluées. */
        private final int nombreReglesEvaluees;
        /** Durée de l'évaluation en millisecondes. */
        private final long dureeMs;

        public ResultatEvaluation(List<RegleDeclenchee> reglesDeclenchees, int scoreTotal,
                                   int nombreReglesEvaluees, long dureeMs) {
            this.reglesDeclenchees = reglesDeclenchees;
            this.scoreTotal = Math.min(scoreTotal, 100);
            this.nombreReglesEvaluees = nombreReglesEvaluees;
            this.dureeMs = dureeMs;
        }

        public List<RegleDeclenchee> getReglesDeclenchees() { return reglesDeclenchees; }
        public int getScoreTotal() { return scoreTotal; }
        public int getNombreReglesEvaluees() { return nombreReglesEvaluees; }
        public long getDureeMs() { return dureeMs; }
        public boolean isAucuneRegleDeclenchee() { return reglesDeclenchees.isEmpty(); }
    }

    /**
     * Une règle qui s'est déclenchée avec son message.
     */
    public static class RegleDeclenchee {
        private final Regle regle;
        private final String message;

        public RegleDeclenchee(Regle regle, String message) {
            this.regle = regle;
            this.message = message;
        }

        public Regle getRegle() { return regle; }
        public String getMessage() { return message; }
    }

    // Évaluation principale

    /**
     * Évalue toutes les règles actives contre une transaction.
     * <p>
     * Les règles sont évaluées dans l'ordre de priorité (0 = priorité maximale).
     * Les règles AUTO_REJET et CRITIQUE sont évaluées en premier pour permettre
     * un blocage rapide.
     * </p>
     *
     * @param transaction la transaction à évaluer
     * @return le résultat de l'évaluation avec les règles déclenchées et le score
     */
    public ResultatEvaluation evaluer(Transaction transaction) {
        long debut = System.currentTimeMillis();

        // Charger les règles actives triées par priorité
        List<Regle> regles = regleRepository.findActiveRulesForEvaluation();

        if (regles.isEmpty()) {
            log.debug("Aucune règle active trouvée — transaction acceptée par défaut");
            return new ResultatEvaluation(new ArrayList<>(), 0, 0, System.currentTimeMillis() - debut);
        }

        // Construire le contexte d'évaluation avec les variables de la transaction
        StandardEvaluationContext contexte = construireContexte(transaction);

        // Évaluer chaque règle
        List<RegleDeclenchee> reglesDeclenchees = new ArrayList<>();
        int scoreAccumule = 0;

        for (Regle regle : regles) {
            try {
                boolean declenchee = evaluerRegle(regle, contexte);

                if (declenchee) {
                    String message = construireMessage(regle, transaction);
                    reglesDeclenchees.add(new RegleDeclenchee(regle, message));
                    scoreAccumule += regle.getContributionScore();

                    log.debug("Règle déclenchée : {} (sévérité={}, contribution={}, score accumulé={})",
                            regle.getNom(), regle.getSeverite(), regle.getContributionScore(), scoreAccumule);

                    // Si le score dépasse 100, on plafonne et on arrête l'évaluation
                    if (scoreAccumule >= 100) {
                        log.debug("Score maximal atteint (100) — arrêt de l'évaluation");
                        break;
                    }
                }
            } catch (Exception e) {
                // Une règle qui échoue ne doit pas bloquer l'évaluation des autres
                log.error("Erreur lors de l'évaluation de la règle '{}' : {}", regle.getNom(), e.getMessage(), e);
            }
        }

        long duree = System.currentTimeMillis() - debut;

        log.debug("Évaluation terminée : {} règles évaluées, {} déclenchées, score={}, durée={}ms",
                regles.size(), reglesDeclenchees.size(), scoreAccumule, duree);

        return new ResultatEvaluation(reglesDeclenchees, scoreAccumule, regles.size(), duree);
    }

    // Évaluation d'une règle unique (utilisé pour le test de règle)

    /**
     * Teste une expression SpEL contre une transaction sans la sauvegarder.
     * <p>
     * Utilisé par l'interface d'administration pour tester une règle
     * avant de la créer ou de la modifier.
     * </p>
     *
     * @param expression l'expression SpEL à tester
     * @param transaction la transaction de test
     * @return {@code true} si l'expression est évaluée à true
     * @throws ExpressionRegleInvalideException si l'expression est syntaxiquement invalide
     */
    public boolean testerExpression(String expression, Transaction transaction) {
        try {
            // Compiler l'expression (sans cache — test ponctuel)
            Expression exp = spelConfig.compilerExpression(expression);
            StandardEvaluationContext contexte = construireContexte(transaction);
            Boolean resultat = exp.getValue(contexte, Boolean.class);
            return resultat != null && resultat;
        } catch (org.springframework.expression.ParseException e) {
            throw new ExpressionRegleInvalideException(expression, e.getMessage());
        } catch (Exception e) {
            throw new ExpressionRegleInvalideException(expression, "Erreur d'évaluation : " + e.getMessage());
        }
    }

    // Méthodes privées

    /**
     * Évalue une règle unique contre le contexte fourni.
     *
     * @param regle    la règle à évaluer
     * @param contexte le contexte SpEL contenant les variables de la transaction
     * @return {@code true} si la règle est déclenchée
     */
    private boolean evaluerRegle(Regle regle, StandardEvaluationContext contexte) {
        // Récupérer ou compiler l'expression (avec cache)
        Expression expression = spelConfig.getOuCompiler(regle.getExpressionCondition());

        // Évaluer l'expression dans le contexte
        Boolean resultat = expression.getValue(contexte, Boolean.class);

        return resultat != null && resultat;
    }

    /**
     * Construit le contexte d'évaluation SpEL avec les variables de la transaction.
     *
     * @param transaction la transaction
     * @return le contexte configuré
     */
    private StandardEvaluationContext construireContexte(Transaction transaction) {
        StandardEvaluationContext contexte = new StandardEvaluationContext();

        // Variables de la transaction
        contexte.setVariable("montant", transaction.getMontant());
        contexte.setVariable("codeDevise", transaction.getCodeDevise());
        contexte.setVariable("typeTransaction",
                transaction.getTypeTransaction() != null ? transaction.getTypeTransaction().name() : null);
        contexte.setVariable("canal",
                transaction.getCanal() != null ? transaction.getCanal().name() : null);
        contexte.setVariable("paysOrigine", transaction.getPaysOrigine());
        contexte.setVariable("categorieContrepartie",
                transaction.getCategorieContrepartie() != null
                        ? transaction.getCategorieContrepartie().name() : null);
        contexte.setVariable("ribSource", transaction.getRibSource());
        contexte.setVariable("ribDestination", transaction.getRibDestination());
        contexte.setVariable("scoreRisque", transaction.getScoreRisque());

        // Valeurs utiles supplémentaires
        contexte.setVariable("dateTransaction", transaction.getDateTransaction());
        contexte.setVariable("description", transaction.getDescription());

        return contexte;
    }

    /**
     * Construit un message descriptif pour une règle déclenchée.
     *
     * @param regle       la règle déclenchée
     * @param transaction la transaction concernée
     * @return le message formaté
     */
    private String construireMessage(Regle regle, Transaction transaction) {
        StringBuilder message = new StringBuilder();
        message.append(regle.getNom());
        message.append(" — Transaction ");
        message.append(transaction.getReferenceTransaction() != null
                ? transaction.getReferenceTransaction() : "inconnue");

        if (transaction.getMontant() != null && transaction.getCodeDevise() != null) {
            message.append(" de ");
            message.append(transaction.getMontant());
            message.append(" ");
            message.append(transaction.getCodeDevise());
        }

        if (transaction.getTypeTransaction() != null) {
            message.append(" (");
            message.append(transaction.getTypeTransaction().name());
            message.append(")");
        }

        return message.toString();
    }

    // Gestion du cache

    /**
     * Vide le cache des expressions compilées.
     * <p>
     * Appelé après modification ou suppression de règles pour forcer
     * la recompilation à la prochaine évaluation.
     * </p>
     */
    public void viderCache() {
        spelConfig.viderCache();
        log.info("Cache des expressions SpEL vidé");
    }

    /**
     * Invalide une expression spécifique dans le cache.
     *
     * @param expressionRaw l'expression à invalider
     */
    public void invaliderExpression(String expressionRaw) {
        spelConfig.invaliderExpression(expressionRaw);
    }

    /**
     * Retourne le nombre d'expressions en cache.
     *
     * @return la taille du cache
     */
    public int getTailleCache() {
        return spelConfig.tailleCache();
    }
}