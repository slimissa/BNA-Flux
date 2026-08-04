package com.bna.flux.service.pipeline;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.exception.DisjoncteurOuvertException;
import com.bna.flux.exception.RibInvalideException;
import com.bna.flux.service.MoteurRegles.RegleDeclenchee;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Contexte transportant une transaction à travers les 5 étapes du pipeline.
 * <p>
 * Le {@link ContextePipeline} est l'objet central du pipeline BNA-FLUX.
 * Il est créé au début du traitement, enrichi par chaque étape, et contient
 * l'état complet de la transaction à chaque instant du pipeline.
 * </p>
 *
 * <p><b>Cycle de vie :</b></p>
 * <ol>
 *   <li>Créé par {@link MoteurPipeline} avant le Stage 1</li>
 *   <li>Enrichi par chaque étape successivement</li>
 *   <li>Marqué comme {@code termine} après le Stage 5</li>
 *   <li>Consulté pour construire la réponse API</li>
 * </ol>
 *
 * <p><b>Contenu du contexte :</b></p>
 * <ul>
 *   <li>La transaction en cours de traitement</li>
 *   <li>Les résultats de chaque étape (succès/échec, messages)</li>
 *   <li>Les alertes générées par les règles déclenchées</li>
 *   <li>Les indicateurs de progression et d'erreur</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Data
@Builder
public class ContextePipeline {

    // Transaction

    /**
     * La transaction en cours de traitement dans le pipeline.
     * <p>
     * Initialisée avec les données brutes de la requête,
     * enrichie à chaque étape, sauvegardée au Stage 5.
     * </p>
     */
    private Transaction transaction;

    // Résultats des étapes

    /**
     * Indique si l'étape de validation (Stage 1) a réussi.
     */
    @Builder.Default
    private boolean validationReussie = false;

    /**
     * Indique si l'étape d'enrichissement (Stage 2) a réussi.
     */
    @Builder.Default
    private boolean enrichissementReussi = false;

    /**
     * Indique si l'étape d'évaluation des règles (Stage 3) a réussi.
     */
    @Builder.Default
    private boolean evaluationReussie = false;

    /**
     * Indique si l'étape de notation (Stage 4) a réussi.
     */
    @Builder.Default
    private boolean notationReussie = false;

    /**
     * Indique si l'étape de persistance (Stage 5) a réussi.
     */
    @Builder.Default
    private boolean persistanceReussie = false;

    // État du pipeline

    /**
     * Indique si le pipeline a été interrompu prématurément.
     * <p>
     * Se produit quand une étape échoue de manière bloquante
     * (ex: RIB invalide, disjoncteur ouvert).
     * </p>
     */
    @Builder.Default
    private boolean interrompu = false;

    /**
     * Indique si le pipeline est terminé (toutes les étapes exécutées ou interrompu).
     */
    @Builder.Default
    private boolean termine = false;

    /**
     * L'étape à laquelle le pipeline s'est arrêté (en cas d'interruption).
     */
    private String etapeArret;

    /**
     * La raison de l'interruption du pipeline.
     */
    private String raisonArret;

    // Données accumulées

    /**
     * Score de risque accumulé (0-100).
     * <p>
     * Calculé par le {@link com.bna.flux.service.MoteurRegles}
     * lors du Stage 3, plafonné à 100.
     * </p>
     */
    @Builder.Default
    private int scoreRisque = 0;

    /**
     * Liste des règles qui se sont déclenchées lors du Stage 3.
     */
    @Builder.Default
    private List<RegleDeclenchee> reglesDeclenchees = new ArrayList<>();

    /**
     * Liste des alertes générées pendant le pipeline.
     * <p>
     * Remplie par le Stage 5 (Persistance) après sauvegarde.
     * </p>
     */
    @Builder.Default
    private List<Alerte> alertesGenerees = new ArrayList<>();

    /**
     * Message de motif combiné (concaténation des messages des règles déclenchées).
     */
    private String motif;

    // Horodatages

    /**
     * Horodatage de début du traitement par le pipeline.
     */
    private LocalDateTime debutTraitement;

    /**
     * Horodatage de fin du traitement par le pipeline.
     */
    private LocalDateTime finTraitement;

    // Méthodes de gestion du cycle de vie

    /**
     * Marque le début du traitement par le pipeline.
     */
    public void demarrer() {
        this.debutTraitement = LocalDateTime.now();
        this.termine = false;
        this.interrompu = false;
    }

    /**
     * Marque la fin normale du pipeline.
     */
    public void terminer() {
        this.finTraitement = LocalDateTime.now();
        this.termine = true;
    }

    /**
     * Interrompt le pipeline à l'étape spécifiée.
     *
     * @param etape  l'étape où l'interruption s'est produite
     * @param raison la raison de l'interruption
     */
    public void interrompre(String etape, String raison) {
        this.interrompu = true;
        this.termine = true;
        this.etapeArret = etape;
        this.raisonArret = raison;
        this.finTraitement = LocalDateTime.now();
    }

    // Méthodes d'accumulation des données

    /**
     * Ajoute des règles déclenchées et accumule leur score.
     *
     * @param regles la liste des règles déclenchées
     * @param score  le score total calculé
     */
    public void ajouterReglesDeclenchees(List<RegleDeclenchee> regles, int score) {
        if (regles != null) {
            this.reglesDeclenchees.addAll(regles);
        }
        this.scoreRisque = Math.min(score, 100);
    }

    /**
     * Ajoute une alerte générée à la liste.
     *
     * @param alerte l'alerte à ajouter
     */
    public void ajouterAlerte(Alerte alerte) {
        if (alerte != null) {
            this.alertesGenerees.add(alerte);
        }
    }

    /**
     * Construit le motif combiné à partir des messages des règles déclenchées.
     *
     * @return le motif combiné, ou {@code null} si aucune règle déclenchée
     */
    public String construireMotif() {
        if (reglesDeclenchees.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reglesDeclenchees.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(reglesDeclenchees.get(i).getMessage());
        }
        this.motif = sb.toString();
        return this.motif;
    }

    // Méthodes d'état

    /**
     * Vérifie si le pipeline a été exécuté avec succès jusqu'au bout.
     *
     * @return {@code true} si toutes les étapes ont réussi
     */
    public boolean estReussi() {
        return validationReussie && enrichissementReussi
                && evaluationReussie && notationReussie && persistanceReussie;
    }

    /**
     * Vérifie si la transaction a été bloquée (statut BLOQUE ou interrompu).
     *
     * @return {@code true} si la transaction est bloquée
     */
    public boolean estTransactionBloquee() {
        return interrompu || (transaction != null && transaction.estBloquee());
    }

    /**
     * Calcule la durée totale du traitement en millisecondes.
     *
     * @return la durée en ms, ou 0 si les horodatages ne sont pas définis
     */
    public long getDureeTraitementMs() {
        if (debutTraitement == null || finTraitement == null) {
            return 0;
        }
        return java.time.Duration.between(debutTraitement, finTraitement).toMillis();
    }

    /**
     * Retourne un résumé lisible du pipeline pour les logs.
     *
     * @return le résumé formaté
     */
    public String getResume() {
        String ref = transaction != null ? transaction.getReferenceTransaction() : "inconnue";
        String statut = transaction != null && transaction.getStatut() != null
                ? transaction.getStatut().name() : "EN_COURS";
        return String.format("Transaction %s — Statut: %s — Score: %d — Durée: %dms — %s",
                ref, statut, scoreRisque, getDureeTraitementMs(),
                interrompu ? "INTERROMPU à " + etapeArret : "TERMINÉ");
    }
}