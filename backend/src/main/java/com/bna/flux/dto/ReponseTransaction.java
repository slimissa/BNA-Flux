package com.bna.flux.dto;

import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.entity.Transaction.TypeTransaction;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour la réponse après traitement d'une transaction par le pipeline.
 * <p>
 * Retourné par {@link com.bna.flux.controller.TransactionController} après
 * soumission ou consultation d'une transaction. Contient toutes les données
 * de la transaction ainsi que le résultat du pipeline (score, statut, alertes).
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "id": 1234,
 *         "referenceTransaction": "BNA-20260804-0001",
 *         "ribSource": "08601000191000748054",
 *         "ribDestination": "01234000123456789012",
 *         "montant": 50000.000,
 *         "codeDevise": "TND",
 *         "nomDevise": "Dinar Tunisien",
 *         "typeTransaction": "VIREMENT",
 *         "canal": "EN_LIGNE",
 *         "dateTransaction": "2026-08-04T09:15:00",
 *         "description": "Paiement fournisseur",
 *         "paysOrigine": "Tunisie",
 *         "categorieContrepartie": "ENTREPRISE",
 *         "scoreRisque": 45.00,
 *         "statutTransaction": "SURVEILLE",
 *         "motif": "Virement > 50k TND; Canal en ligne",
 *         "traiteLe": "2026-08-04T09:15:01",
 *         "alertes": [ ... ],
 *         "nombreAlertes": 2
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
public class ReponseTransaction {

    /**
     * Statut global de la réponse.
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Identifiant unique de la transaction en base.
     */
    private Long id;

    /**
     * Référence unique de la transaction (format BNA-YYYYMMDD-XXXX).
     */
    private String referenceTransaction;

    /**
     * RIB source de la transaction (émetteur, 20 chiffres).
     */
    private String ribSource;

    /**
     * RIB destination de la transaction (bénéficiaire, 20 chiffres).
     */
    private String ribDestination;

    /**
     * Montant de la transaction dans l'unité principale de la devise.
     */
    private BigDecimal montant;

    /**
     * Code ISO 4217 de la devise.
     */
    private String codeDevise;

    /**
     * Nom complet de la devise en français.
     * <p>
     * Récupéré depuis l'entité {@link com.bna.flux.entity.Devise} associée.
     * </p>
     */
    private String nomDevise;

    /**
     * Symbole de la devise pour l'affichage formaté.
     */
    private String symboleDevise;

    /**
     * Type de transaction (VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT).
     */
    private TypeTransaction typeTransaction;

    /**
     * Canal d'initiation (AGENCE, DAB, EN_LIGNE, MOBILE).
     */
    private Canal canal;

    /**
     * Date et heure d'exécution de la transaction.
     */
    private LocalDateTime dateTransaction;

    /**
     * Description ou motif de la transaction.
     */
    private String description;

    /**
     * Pays d'origine déterminé lors de l'enrichissement (Stage 2).
     */
    private String paysOrigine;

    /**
     * Catégorie de la contrepartie déterminée lors de l'enrichissement.
     */
    private String categorieContrepartie;

    /**
     * Score de risque calculé (0.00 à 100.00).
     * <p>
     * Déterminé lors du Stage 4 (Notation) comme la somme des contributions
     * des règles déclenchées, plafonnée à 100.
     * </p>
     */
    private BigDecimal scoreRisque;

    /**
     * Statut final de la transaction après le pipeline.
     * <ul>
     *   <li>ACCEPTE — score &lt; 30</li>
     *   <li>SURVEILLE — 30 ≤ score ≤ 70</li>
     *   <li>BLOQUE — score &gt; 70 ou disjoncteur ouvert</li>
     * </ul>
     */
    private StatutTransaction statutTransaction;

    /**
     * Motif de rejet ou de surveillance.
     * <p>
     * Concaténation des messages des règles déclenchées.
     * Null si la transaction est ACCEPTEE sans alerte.
     * </p>
     */
    private String motif;

    /**
     * Date et heure de traitement par le pipeline.
     */
    private LocalDateTime traiteLe;

    /**
     * Date de création de l'enregistrement en base.
     */
    private LocalDateTime dateCreation;

    /**
     * Liste des alertes générées par les règles déclenchées.
     * <p>
     * Vide si aucune règle n'a été déclenchée.
     * </p>
     */
    private List<ReponseAlerte> alertes;

    /**
     * Nombre total d'alertes générées pour cette transaction.
     */
    private int nombreAlertes;

    /**
     * Indique si la piste d'audit est disponible pour cette transaction.
     */
    @Builder.Default
    private boolean pisteAuditDisponible = true;

    // Méthodes utilitaires

    /**
     * Crée une réponse transaction à partir des données essentielles.
     * <p>
     * Les champs non fournis seront null (et exclus du JSON via
     * {@link JsonInclude.Include#NON_NULL}).
     * </p>
     *
     * @param id                    l'identifiant de la transaction
     * @param referenceTransaction  la référence unique
     * @param statutTransaction     le statut final
     * @param scoreRisque           le score de risque
     * @return la réponse construite
     */
    public static ReponseTransaction resumer(Long id, String referenceTransaction,
                                              StatutTransaction statutTransaction, BigDecimal scoreRisque) {
        return ReponseTransaction.builder()
                .statut("SUCCES")
                .id(id)
                .referenceTransaction(referenceTransaction)
                .statutTransaction(statutTransaction)
                .scoreRisque(scoreRisque)
                .build();
    }

    /**
     * Formate le montant avec le symbole de la devise.
     *
     * @return le montant formaté (ex: "د.ت 50,000"), ou null si pas de symbole
     */
    public String getMontantFormate() {
        if (symboleDevise != null && montant != null) {
            return symboleDevise + " " + String.format("%,.3f", montant);
        }
        return null;
    }
}