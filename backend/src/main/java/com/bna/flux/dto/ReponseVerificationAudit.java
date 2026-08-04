package com.bna.flux.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour la réponse de vérification de l'intégrité de la piste d'audit.
 * <p>
 * Retourné par {@link com.bna.flux.controller.TransactionController#verifierPisteAudit(Long)}
 * après avoir recalculé et comparé tous les hashs de la chaîne d'audit
 * d'une transaction.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "transactionId": 1234,
 *         "referenceTransaction": "BNA-20260804-0001",
 *         "chaineIntacte": true,
 *         "nombreEntrees": 5,
 *         "premiereEntree": "2026-08-04T09:15:00",
 *         "derniereEntree": "2026-08-04T09:15:02",
 *         "entrees": [
 *             {
 *                 "id": 1,
 *                 "etape": "VALIDATION",
 *                 "action": "RIBS_VALIDES",
 *                 "hashStocke": "a3f2c8...",
 *                 "hashCalcule": "a3f2c8...",
 *                 "hashVerifie": true
 *             },
 *             ...
 *         ]
 *     }
 * }
 * </pre>
 *
 * <p><b>En cas de chaîne corrompue :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "transactionId": 1234,
 *         "chaineIntacte": false,
 *         "nombreEntrees": 5,
 *         "entreeCorrompue": 3,
 *         "messageErreur": "Le hash de l'entrée 3 ne correspond pas. La chaîne a été altérée."
 *     }
 * }
 * </pre>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReponseVerificationAudit {

    /**
     * Statut global de la réponse.
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Identifiant de la transaction vérifiée.
     */
    private Long transactionId;

    /**
     * Référence de la transaction (format BNA-YYYYMMDD-XXXX).
     */
    private String referenceTransaction;

    /**
     * Indique si la chaîne de hachage est intacte.
     * <p>
     * {@code true} — Tous les hashs correspondent, la piste d'audit
     * n'a pas été altérée.
     * </p>
     * <p>
     * {@code false} — Au moins un hash ne correspond pas. La piste
     * d'audit a été modifiée ou corrompue.
     * </p>
     */
    private boolean chaineIntacte;

    /**
     * Nombre total d'entrées dans la piste d'audit.
     * <p>
     * Une transaction traitée normalement par le pipeline complet
     * génère exactement 5 entrées (une par étape).
     * </p>
     */
    private int nombreEntrees;

    /**
     * Horodatage de la première entrée d'audit.
     */
    private LocalDateTime premiereEntree;

    /**
     * Horodatage de la dernière entrée d'audit.
     */
    private LocalDateTime derniereEntree;

    /**
     * Détail de chaque entrée d'audit avec le résultat de la vérification.
     * <p>
     * Null si la vérification est demandée en mode résumé.
     * </p>
     */
    private List<EntreeVerifiee> entrees;

    /**
     * Index (1-based) de la première entrée corrompue.
     * <p>
     * Null si la chaîne est intacte.
     * </p>
     */
    private Integer entreeCorrompue;

    /**
     * Message d'erreur descriptif en cas de corruption.
     * <p>
     * Exemple : "Le hash de l'entrée 3 (étape EVALUATION_REGLES) ne correspond pas.
     * Le hash stocké est 'abc123...' mais le hash recalculé est 'def456...'.
     * La chaîne a été altérée à partir de cette entrée."
     * </p>
     */
    private String messageErreur;

    /**
     * Durée de la vérification en millisecondes.
     * <p>
     * Utile pour le monitoring des performances.
     * </p>
     */
    private long dureeVerificationMs;

    // Classe interne

    /**
     * Une entrée d'audit avec le résultat de sa vérification individuelle.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EntreeVerifiee {

        /**
         * Identifiant de l'entrée d'audit.
         */
        private Long id;

        /**
         * Étape du pipeline (VALIDATION, ENRICHISSEMENT, etc.).
         */
        private String etape;

        /**
         * Action effectuée.
         */
        private String action;

        /**
         * Opérateur ayant effectué l'action.
         */
        private String operateur;

        /**
         * Horodatage de l'entrée.
         */
        private LocalDateTime horodatage;

        /**
         * Hash stocké en base de données.
         */
        private String hashStocke;

        /**
         * Hash recalculé lors de la vérification.
         */
        private String hashCalcule;

        /**
         * Indique si le hash stocké correspond au hash recalculé.
         */
        private boolean hashVerifie;

        /**
         * Hash de l'entrée précédente (pour traçabilité).
         */
        private String hashPrecedent;
    }
}