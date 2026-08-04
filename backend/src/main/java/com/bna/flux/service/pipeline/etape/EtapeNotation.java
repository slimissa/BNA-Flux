package com.bna.flux.service.pipeline.etape;

import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.service.ServiceDisjoncteur;
import com.bna.flux.service.pipeline.ContextePipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 4 du pipeline — Notation du risque et décision.
 * <p>
 * Cette étape prend le score de risque calculé au Stage 3 et détermine
 * le statut final de la transaction (ACCEPTEE, SURVEILLEE, ou BLOQUEE).
 * Elle gère également l'enregistrement des échecs auprès des disjoncteurs
 * pour les transactions bloquées.
 * </p>
 *
 * <p><b>Seuils de décision (configurables) :</b></p>
 * <table border="1">
 *   <tr><th>Score</th><th>Statut</th><th>Action</th></tr>
 *   <tr><td>0 — 29</td><td>ACCEPTE</td><td>Transaction autorisée</td></tr>
 *   <tr><td>30 — 70</td><td>SURVEILLE</td><td>Transaction autorisée mais sous surveillance</td></tr>
 *   <tr><td>71 — 100</td><td>BLOQUE</td><td>Transaction rejetée automatiquement</td></tr>
 * </table>
 *
 * <p><b>Disjoncteurs :</b></p>
 * <p>
 * Lorsqu'une transaction est BLOQUEE, un échec est enregistré pour :
 * </p>
 * <ul>
 *   <li>Le RIB source (COMPTE_SOURCE)</li>
 *   <li>Le RIB destination (COMPTE_DESTINATION)</li>
 *   <li>L'agence source</li>
 *   <li>Le canal utilisé</li>
 * </ul>
 * <p>
 * Si le nombre d'échecs atteint le seuil configuré, le disjoncteur
 * correspondant s'ouvre et bloque toutes les transactions futures pour
 * cette cible.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class EtapeNotation {

    private final ServiceDisjoncteur serviceDisjoncteur;

    /**
     * Seuil à partir duquel une transaction est mise sous surveillance.
     */
    @Value("${bna.notation.seuil-surveille:30}")
    private int seuilSurveille;

    /**
     * Seuil à partir duquel une transaction est bloquée.
     */
    @Value("${bna.notation.seuil-bloque:71}")
    private int seuilBloque;

    /**
     * Score maximum possible.
     */
    @Value("${bna.notation.score-max:100}")
    private int scoreMax;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param serviceDisjoncteur le service de gestion des disjoncteurs
     */
    public EtapeNotation(ServiceDisjoncteur serviceDisjoncteur) {
        this.serviceDisjoncteur = serviceDisjoncteur;
    }

    // Exécution de l'étape

    /**
     * Exécute le Stage 4 — Notation du risque et décision.
     * <p>
     * Détermine le statut final en fonction du score et enregistre
     * les échecs auprès des disjoncteurs si la transaction est bloquée.
     * </p>
     *
     * @param contexte le contexte du pipeline contenant la transaction évaluée
     */
    public void executer(ContextePipeline contexte) {
        Transaction transaction = contexte.getTransaction();
        int score = contexte.getScoreRisque();

        log.debug("Stage 4 — Notation de la transaction : {} (score={})",
                transaction.getReferenceTransaction() != null ? transaction.getReferenceTransaction() : "nouvelle",
                score);

        try {
            // 1. Déterminer le statut en fonction du score
            StatutTransaction statut = determinerStatut(score);
            transaction.setStatut(statut);

            log.info("Stage 4 — Score : {}/{} → Statut : {}", score, scoreMax, statut.name());

            // 2. Si la transaction est bloquée, enregistrer les échecs auprès des disjoncteurs
            if (statut == StatutTransaction.BLOQUE) {
                enregistrerEchecsDisjoncteurs(transaction);
            }

            // 3. Si la transaction était en MI_OUVERT (test), confirmer le résultat
            gererTestDisjoncteur(transaction, statut);

            // Succès
            contexte.setNotationReussie(true);
            log.debug("Stage 4 réussi — Transaction notée : {} ({})",
                    transaction.getReferenceTransaction(), statut.name());

        } catch (Exception e) {
            log.error("Stage 4 — Erreur lors de la notation : {}", e.getMessage(), e);
            // En cas d'erreur de notation, on accepte par défaut (fail-open)
            transaction.setStatut(StatutTransaction.ACCEPTE);
            contexte.setNotationReussie(true);
        }
    }

    // Méthodes privées

    /**
     * Détermine le statut de la transaction en fonction de son score.
     *
     * @param score le score de risque (0-100)
     * @return le statut correspondant
     */
    private StatutTransaction determinerStatut(int score) {
        if (score >= seuilBloque) {
            return StatutTransaction.BLOQUE;
        } else if (score >= seuilSurveille) {
            return StatutTransaction.SURVEILLE;
        } else {
            return StatutTransaction.ACCEPTE;
        }
    }

    /**
     * Enregistre les échecs auprès des disjoncteurs pour une transaction bloquée.
     * <p>
     * Un échec est enregistré pour chaque type de cible pertinent.
     * Si le nombre d'échecs atteint le seuil, le disjoncteur s'ouvre automatiquement.
     * </p>
     *
     * @param transaction la transaction bloquée
     */
    private void enregistrerEchecsDisjoncteurs(Transaction transaction) {
        try {
            // RIB source
            if (transaction.getRibSource() != null) {
                serviceDisjoncteur.enregistrerEchec(
                        com.bna.flux.entity.EtatDisjoncteur.TypeCible.COMPTE_SOURCE,
                        transaction.getRibSource()
                );
            }

            // RIB destination
            if (transaction.getRibDestination() != null) {
                serviceDisjoncteur.enregistrerEchec(
                        com.bna.flux.entity.EtatDisjoncteur.TypeCible.COMPTE_DESTINATION,
                        transaction.getRibDestination()
                );
            }

            // Agence source
            String codeAgence = transaction.getCodeAgenceSource();
            if (codeAgence != null && !codeAgence.isEmpty()) {
                serviceDisjoncteur.enregistrerEchec(
                        com.bna.flux.entity.EtatDisjoncteur.TypeCible.AGENCE,
                        codeAgence
                );
            }

            // Canal
            if (transaction.getCanal() != null) {
                serviceDisjoncteur.enregistrerEchec(
                        com.bna.flux.entity.EtatDisjoncteur.TypeCible.CANAL,
                        transaction.getCanal().name()
                );
            }

            log.debug("Échecs enregistrés auprès des disjoncteurs pour la transaction {}",
                    transaction.getReferenceTransaction());

        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement des échecs disjoncteurs : {}", e.getMessage(), e);
            // Ne pas bloquer le pipeline si l'enregistrement échoue
        }
    }

    /**
     * Gère le résultat du test pour un disjoncteur en état MI_OUVERT.
     * <p>
     * Si un disjoncteur était en MI_OUVERT et que cette transaction
     * était le test :
     * </p>
     * <ul>
     *   <li>Transaction acceptée → test réussi → disjoncteur repasse à FERMÉ</li>
     *   <li>Transaction bloquée → test échoué → disjoncteur retourne à OUVERT</li>
     * </ul>
     *
     * @param transaction la transaction
     * @param statut      le statut déterminé
     */
    private void gererTestDisjoncteur(Transaction transaction, StatutTransaction statut) {
        try {
            // Vérifier pour le RIB source
            if (transaction.getRibSource() != null) {
                gererTestPourCible(
                        com.bna.flux.entity.EtatDisjoncteur.TypeCible.COMPTE_SOURCE,
                        transaction.getRibSource(),
                        statut
                );
            }

            // Vérifier pour le RIB destination
            if (transaction.getRibDestination() != null) {
                gererTestPourCible(
                        com.bna.flux.entity.EtatDisjoncteur.TypeCible.COMPTE_DESTINATION,
                        transaction.getRibDestination(),
                        statut
                );
            }
        } catch (Exception e) {
            log.debug("Erreur lors de la gestion du test disjoncteur : {}", e.getMessage());
        }
    }

    /**
     * Gère le résultat du test pour une cible spécifique.
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     * @param statut           le statut de la transaction
     */
    private void gererTestPourCible(com.bna.flux.entity.EtatDisjoncteur.TypeCible typeCible,
                                     String identifiantCible, StatutTransaction statut) {
        if (statut == StatutTransaction.BLOQUE) {
            serviceDisjoncteur.confirmerTestEchoue(typeCible, identifiantCible);
        } else {
            serviceDisjoncteur.confirmerTestReussi(typeCible, identifiantCible);
        }
    }

    // Accesseurs pour les seuils (utilisés par les tests)

    /**
     * Retourne le seuil de surveillance configuré.
     *
     * @return le seuil de surveillance
     */
    public int getSeuilSurveille() {
        return seuilSurveille;
    }

    /**
     * Retourne le seuil de blocage configuré.
     *
     * @return le seuil de blocage
     */
    public int getSeuilBloque() {
        return seuilBloque;
    }

    /**
     * Retourne le score maximum configuré.
     *
     * @return le score maximum
     */
    public int getScoreMax() {
        return scoreMax;
    }
}