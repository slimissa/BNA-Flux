package com.bna.flux.service.pipeline.etape;

import com.bna.flux.entity.Transaction;
import com.bna.flux.service.MoteurRegles;
import com.bna.flux.service.MoteurRegles.RegleDeclenchee;
import com.bna.flux.service.MoteurRegles.ResultatEvaluation;
import com.bna.flux.service.pipeline.ContextePipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage 3 du pipeline — Évaluation des règles de surveillance.
 * <p>
 * Cette étape exécute le moteur de règles SpEL contre la transaction
 * enrichie. Chaque règle active est évaluée séquentiellement dans l'ordre
 * de priorité. Les règles déclenchées génèrent une contribution au score
 * de risque et seront converties en alertes au Stage 5.
 * </p>
 *
 * <p><b>Processus d'évaluation :</b></p>
 * <ol>
 *   <li>Charger toutes les règles actives depuis la base (triées par priorité)</li>
 *   <li>Construire le contexte SpEL avec les variables de la transaction</li>
 *   <li>Pour chaque règle :
 *     <ul>
 *       <li>Compiler l'expression SpEL (ou récupérer du cache)</li>
 *       <li>Évaluer dans le contexte de la transaction</li>
 *       <li>Si déclenchée : ajouter la contribution au score, collecter la règle</li>
 *     </ul>
 *   </li>
 *   <li>Arrêter si le score atteint 100 (plafond)</li>
 * </ol>
 *
 * <p><b>Variables SpEL disponibles :</b></p>
 * <ul>
 *   <li>{@code montant} — BigDecimal</li>
 *   <li>{@code codeDevise} — String</li>
 *   <li>{@code typeTransaction} — String (VIREMENT, CHEQUE, etc.)</li>
 *   <li>{@code canal} — String (AGENCE, DAB, EN_LIGNE, MOBILE)</li>
 *   <li>{@code paysOrigine} — String ou null (enrichi au Stage 2)</li>
 *   <li>{@code categorieContrepartie} — String ou null</li>
 *   <li>{@code ribSource}, {@code ribDestination} — String (20 chiffres)</li>
 * </ul>
 *
 * <p><b>Performance :</b></p>
 * <ul>
 *   <li>Les expressions sont compilées une fois et mises en cache</li>
 *   <li>L'évaluation s'arrête si le score atteint 100</li>
 *   <li>Les règles AUTO_REJET et CRITIQUE sont évaluées en premier (priorité basse)</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class EtapeEvaluationRegles {

    private final MoteurRegles moteurRegles;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param moteurRegles le moteur de règles SpEL
     */
    public EtapeEvaluationRegles(MoteurRegles moteurRegles) {
        this.moteurRegles = moteurRegles;
    }

    // Exécution de l'étape

    /**
     * Exécute le Stage 3 — Évaluation des règles de surveillance.
     * <p>
     * L'évaluation ne bloque pas la transaction (sauf règle AUTO_REJET).
     * Même si toutes les règles échouent à s'évaluer, le pipeline continue.
     * </p>
     *
     * @param contexte le contexte du pipeline contenant la transaction enrichie
     */
    public void executer(ContextePipeline contexte) {
        Transaction transaction = contexte.getTransaction();
        log.debug("Stage 3 — Évaluation des règles pour la transaction : {}",
                transaction.getReferenceTransaction() != null ? transaction.getReferenceTransaction() : "nouvelle");

        try {
            // Exécuter le moteur de règles
            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            // Accumuler les règles déclenchées et le score
            List<RegleDeclenchee> reglesDeclenchees = resultat.getReglesDeclenchees();
            int scoreTotal = resultat.getScoreTotal();

            contexte.ajouterReglesDeclenchees(reglesDeclenchees, scoreTotal);

            // Construire le motif combiné
            String motif = contexte.construireMotif();
            transaction.setMotif(motif);

            // Mettre à jour le score de risque de la transaction
            transaction.setScoreRisque(java.math.BigDecimal.valueOf(scoreTotal));

            // Journaliser le résultat
            if (reglesDeclenchees.isEmpty()) {
                log.debug("Stage 3 réussi — Aucune règle déclenchée sur {} règles évaluées en {}ms",
                        resultat.getNombreReglesEvaluees(), resultat.getDureeMs());
            } else {
                log.info("Stage 3 — {} règle(s) déclenchée(s) sur {} évaluées — Score : {}/100 — Durée : {}ms",
                        reglesDeclenchees.size(),
                        resultat.getNombreReglesEvaluees(),
                        scoreTotal,
                        resultat.getDureeMs());

                // Logger le détail de chaque règle déclenchée
                for (RegleDeclenchee regle : reglesDeclenchees) {
                    log.debug("  → Règle déclenchée : {} (sévérité={}, contribution={})",
                            regle.getRegle().getNom(),
                            regle.getRegle().getSeverite(),
                            regle.getRegle().getContributionScore());
                }
            }

            // Succès
            contexte.setEvaluationReussie(true);

        } catch (Exception e) {
            // L'évaluation qui échoue ne bloque pas la transaction
            log.error("Stage 3 — Erreur lors de l'évaluation des règles : {}", e.getMessage(), e);
            contexte.setEvaluationReussie(true); // On continue malgré l'erreur
            // Score reste à 0 si l'évaluation échoue
            transaction.setScoreRisque(java.math.BigDecimal.ZERO);
        }
    }

    // Méthodes utilitaires publiques

    /**
     * Teste une expression SpEL contre une transaction existante.
     * <p>
     * Utilisé par l'interface d'administration pour tester une règle
     * avant sa création ou modification. Ne modifie pas la transaction.
     * </p>
     *
     * @param expression  l'expression SpEL à tester
     * @param transaction la transaction de test
     * @return {@code true} si l'expression est évaluée à true
     */
    public boolean testerExpression(String expression, Transaction transaction) {
        return moteurRegles.testerExpression(expression, transaction);
    }

    /**
     * Vide le cache des expressions compilées.
     * <p>
     * Appelé après modification de règles pour forcer la recompilation.
     * </p>
     */
    public void viderCacheRegles() {
        moteurRegles.viderCache();
        log.info("Cache des expressions SpEL vidé");
    }

    /**
     * Retourne le nombre d'expressions en cache.
     *
     * @return la taille du cache SpEL
     */
    public int getTailleCache() {
        return moteurRegles.getTailleCache();
    }
}