package com.bna.flux.service.pipeline;

import com.bna.flux.entity.Transaction;
import com.bna.flux.service.ServiceAudit;
import com.bna.flux.service.pipeline.etape.EtapeEnrichissement;
import com.bna.flux.service.pipeline.etape.EtapeEvaluationRegles;
import com.bna.flux.service.pipeline.etape.EtapeNotation;
import com.bna.flux.service.pipeline.etape.EtapePersistance;
import com.bna.flux.service.pipeline.etape.EtapeValidation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrateur du pipeline de traitement des transactions BNA-FLUX.
 * <p>
 * Le {@link MoteurPipeline} est le chef d'orchestre qui exécute séquentiellement
 * les 5 étapes du pipeline pour chaque transaction soumise au système.
 * Il gère le cycle de vie du {@link ContextePipeline} et garantit que :
 * </p>
 * <ul>
 *   <li>Les étapes sont exécutées dans l'ordre (1 → 2 → 3 → 4 → 5)</li>
 *   <li>Une interruption à une étape arrête immédiatement le pipeline</li>
 *   <li>L'audit est enregistré pour chaque étape exécutée</li>
 *   <li>Les erreurs sont capturées et logguées sans faire tomber le système</li>
 * </ul>
 *
 * <p><b>Flux d'exécution :</b></p>
 * <pre>
 * Transaction → ContextePipeline
 *    │
 *    ├─ Stage 1: Validation (RIB, devise, disjoncteurs)
 *    │   └─ Échec → INTERROMPU (BLOQUE)
 *    │
 *    ├─ Stage 2: Enrichissement (pays, contrepartie)
 *    │   └─ Continue toujours (non bloquant)
 *    │
 *    ├─ Stage 3: Évaluation des règles (SpEL)
 *    │   └─ Continue toujours (non bloquant)
 *    │
 *    ├─ Stage 4: Notation (score → statut, disjoncteurs)
 *    │   └─ Continue toujours
 *    │
 *    └─ Stage 5: Persistance (sauvegarde, alertes, audit, emails)
 *        └─ Échec → INTERROMPU (erreur technique)
 * </pre>
 *
 * <p><b>Propriétés du pipeline :</b></p>
 * <ul>
 *   <li><b>Synchrone</b> — Les étapes sont exécutées dans le thread appelant</li>
 *   <li><b>Interruptible</b> — Une étape peut interrompre le pipeline</li>
 *   <li><b>Traçable</b> — Chaque étape enregistre son résultat dans l'audit</li>
 *   <li><b>Résilient</b> — Les erreurs non bloquantes ne stoppent pas le pipeline</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class MoteurPipeline {

    private final EtapeValidation etapeValidation;
    private final EtapeEnrichissement etapeEnrichissement;
    private final EtapeEvaluationRegles etapeEvaluationRegles;
    private final EtapeNotation etapeNotation;
    private final EtapePersistance etapePersistance;
    private final ServiceAudit serviceAudit;

    /**
     * Constructeur avec injection des 5 étapes et du service d'audit.
     *
     * @param etapeValidation       Stage 1 — Validation
     * @param etapeEnrichissement   Stage 2 — Enrichissement
     * @param etapeEvaluationRegles Stage 3 — Évaluation des règles
     * @param etapeNotation         Stage 4 — Notation
     * @param etapePersistance      Stage 5 — Persistance
     * @param serviceAudit          Service d'audit pour la traçabilité
     */
    public MoteurPipeline(EtapeValidation etapeValidation,
                          EtapeEnrichissement etapeEnrichissement,
                          EtapeEvaluationRegles etapeEvaluationRegles,
                          EtapeNotation etapeNotation,
                          EtapePersistance etapePersistance,
                          ServiceAudit serviceAudit) {
        this.etapeValidation = etapeValidation;
        this.etapeEnrichissement = etapeEnrichissement;
        this.etapeEvaluationRegles = etapeEvaluationRegles;
        this.etapeNotation = etapeNotation;
        this.etapePersistance = etapePersistance;
        this.serviceAudit = serviceAudit;
    }

    // Point d'entrée principal

    /**
     * Exécute le pipeline complet sur une transaction.
     * <p>
     * C'est le point d'entrée unique pour le traitement des transactions.
     * Appelé par {@link com.bna.flux.service.ServiceTransaction} après
     * avoir créé l'entité Transaction à partir du DTO de requête.
     * </p>
     *
     * @param transaction la transaction à traiter
     * @return le contexte du pipeline avec le résultat complet
     */
    public ContextePipeline executer(Transaction transaction) {
        log.info("Démarrage du pipeline pour la transaction : {}",
                transaction.getReferenceTransaction() != null ? transaction.getReferenceTransaction() : "nouvelle");

        // Créer le contexte du pipeline
        ContextePipeline contexte = ContextePipeline.builder()
                .transaction(transaction)
                .build();
        contexte.demarrer();

        try {
            // Stage 1 — Validation
            log.debug(">>> Stage 1 — Validation");
            etapeValidation.executer(contexte);
            enregistrerAudit(contexte, "VALIDATION",
                    contexte.isValidationReussie() ? "VALIDATION_REUSSIE" : "VALIDATION_ECHOUEE");

            if (contexte.isInterrompu()) {
                log.warn("Pipeline interrompu après Stage 1 — {}", contexte.getRaisonArret());
                terminerPipeline(contexte);
                return contexte;
            }

            // Stage 2 — Enrichissement
            log.debug(">>> Stage 2 — Enrichissement");
            etapeEnrichissement.executer(contexte);
            enregistrerAudit(contexte, "ENRICHISSEMENT", "ENRICHISSEMENT_TERMINE");

            // L'enrichissement n'interrompt jamais le pipeline
            // (même en cas d'erreur, on continue)

            // Stage 3 — Évaluation des règles
            log.debug(">>> Stage 3 — Évaluation des règles");
            etapeEvaluationRegles.executer(contexte);
            enregistrerAudit(contexte, "EVALUATION_REGLES",
                    "REGLES_EVALUEES_" + contexte.getReglesDeclenchees().size() + "_DECLENCHEES");

            // L'évaluation n'interrompt jamais le pipeline

            // Stage 4 — Notation
            log.debug(">>> Stage 4 — Notation (score={})", contexte.getScoreRisque());
            etapeNotation.executer(contexte);
            enregistrerAudit(contexte, "NOTATION",
                    "SCORE_" + contexte.getScoreRisque() + "_STATUT_" +
                    (transaction.getStatut() != null ? transaction.getStatut().name() : "INCONNU"));

            // La notation n'interrompt jamais le pipeline

            // Stage 5 — Persistance
            log.debug(">>> Stage 5 — Persistance");
            etapePersistance.executer(contexte);
            // enregistrerAudit(contexte, "PERSISTANCE",
// (commented out — duplicate audit)

            if (contexte.isInterrompu()) {
                log.warn("Pipeline interrompu après Stage 5 — {}", contexte.getRaisonArret());
            }

        } catch (Exception e) {
            log.error("Erreur inattendue dans le pipeline : {}", e.getMessage(), e);
            contexte.interrompre("INCONNU", "Erreur inattendue : " + e.getMessage());
        }

        terminerPipeline(contexte);
        return contexte;
    }

    // Méthodes privées

    /**
     * Finalise le pipeline et loggue le résumé.
     *
     * @param contexte le contexte du pipeline
     */
    private void terminerPipeline(ContextePipeline contexte) {
        contexte.terminer();
        log.info("Pipeline terminé — {}", contexte.getResume());
    }

    /**
     * Enregistre une entrée d'audit pour une étape du pipeline.
     *
     * @param contexte le contexte du pipeline
     * @param etape    le nom de l'étape
     * @param action   l'action effectuée
     */
    private void enregistrerAudit(ContextePipeline contexte, String etape, String action) {
        try {
            Transaction transaction = contexte.getTransaction();
            if (transaction == null || transaction.getId() == null) {
                // La transaction n'a pas encore d'ID (pas encore persistée)
                // On attend le Stage 5 pour l'audit complet
                return;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("referenceTransaction", transaction.getReferenceTransaction());
            details.put("scoreActuel", contexte.getScoreRisque());
            details.put("interrompu", contexte.isInterrompu());

            serviceAudit.enregistrer(transaction, etape, action, details, "SYSTEME");

        } catch (Exception e) {
            log.debug("Impossible d'enregistrer l'audit pour l'étape {} : {}", etape, e.getMessage());
        }
    }
}