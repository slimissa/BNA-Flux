package com.bna.flux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant une entrée dans la piste d'audit hash-chaînée.
 * <p>
 * Chaque étape du pipeline (validation, enrichissement, évaluation des règles,
 * notation, persistance) ajoute une entrée d'audit pour chaque transaction.
 * Les entrées sont chaînées par hachage SHA-256 pour garantir l'intégrité
 * et détecter toute tentative de falsification.
 * </p>
 *
 * <p><b>Principe de la chaîne de hachage :</b></p>
 * <ol>
 *   <li>La première entrée d'audit pour une transaction a {@code hashPrecedent} = null.</li>
 *   <li>Chaque entrée suivante référence le {@code hashCourant} de l'entrée précédente.</li>
 *   <li>{@code hashCourant = SHA-256(hashPrecedent + transactionId + etape + action + detail + horodatage + operateur)}</li>
 *   <li>Toute modification d'une entrée brise la chaîne — détectable par recalcul.</li>
 * </ol>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Les entrées d'audit sont immuables après création (pas de {@code @PreUpdate}).</li>
 *   <li>La suppression physique est interdite — aucune API ne permet de supprimer une entrée.</li>
 *   <li>La vérification de la chaîne recalcule tous les hashs et compare avec les valeurs stockées.</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>{@code @ManyToOne Transaction} — Transaction concernée par cette entrée d'audit.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "entrees_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntreeAudit {

    /**
     * Identifiant unique généré automatiquement.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Transaction concernée par cette entrée d'audit.
     * <p>
     * Relation obligatoire. Chargée en LAZY car les entrées d'audit
     * sont généralement consultées par lot pour une transaction donnée.
     * </p>
     */
    @NotNull(message = "La transaction est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    /**
     * Étape du pipeline ayant généré cette entrée d'audit.
     * <p>
     * Valeurs possibles :
     * </p>
     * <ul>
     *   <li>{@code VALIDATION} — Stage 1 : validation RIB, devise, disjoncteur</li>
     *   <li>{@code ENRICHISSEMENT} — Stage 2 : pays d'origine, catégorie contrepartie</li>
     *   <li>{@code EVALUATION_REGLES} — Stage 3 : évaluation des règles SpEL</li>
     *   <li>{@code NOTATION} — Stage 4 : calcul du score, décision disjoncteur</li>
     *   <li>{@code PERSISTANCE} — Stage 5 : sauvegarde finale</li>
     * </ul>
     */
    @NotBlank(message = "L'étape est obligatoire")
    @Size(max = 30, message = "L'étape ne doit pas dépasser 30 caractères")
    @Column(name = "etape", length = 30, nullable = false)
    private String etape;

    /**
     * Action effectuée durant cette étape.
     * <p>
     * Exemples : "RIB_SOURCE_VALIDE", "RIB_DESTINATION_INVALIDE",
     * "REGLE_DECLENCHEE", "SCORE_CALCULE", "DISJONCTEUR_OUVERT",
     * "TRANSACTION_ACCEPTEE", "TRANSACTION_BLOQUEE".
     * </p>
     */
    @NotBlank(message = "L'action est obligatoire")
    @Size(max = 50, message = "L'action ne doit pas dépasser 50 caractères")
    @Column(name = "action", length = 50, nullable = false)
    private String action;

    /**
     * Détail de l'action au format JSON.
     * <p>
     * Contient les données spécifiques à l'étape. Exemples :
     * </p>
     * <ul>
     *   <li>Validation : {"ribSource": "08601000191000748054", "cleCalculee": "54", "cleFournie": "54", "valide": true}</li>
     *   <li>Règle déclenchée : {"regleId": 3, "nomRegle": "Virement > 50k", "severite": "ELEVE"}</li>
     *   <li>Notation : {"scoreAvant": 15, "scoreApres": 45, "contribution": 30}</li>
     * </ul>
     * <p>
     * Stocké en VARCHAR(2000) — suffisant pour la majorité des cas.
     * Les détails exceptionnellement longs sont tronqués avec "...[tronqué]".
     * </p>
     */
    @Size(max = 2000, message = "Le détail ne doit pas dépasser 2000 caractères")
    @Column(name = "detail", length = 2000)
    private String detail;

    /**
     * Hash de l'entrée précédente dans la chaîne.
     * <p>
     * {@code null} pour la première entrée d'une transaction.
     * Pour les entrées suivantes : SHA-256 de l'entrée précédente.
     * </p>
     */
    @Column(name = "hash_precedent", length = 64)
    private String hashPrecedent;

    /**
     * Hash de cette entrée d'audit.
     * <p>
     * Calculé comme :
     * {@code SHA-256(hashPrecedent + "|" + transactionId + "|" + etape + "|" + action + "|" + detail + "|" + horodatage + "|" + operateur)}
     * </p>
     */
    @Column(name = "hash_courant", length = 64, nullable = false)
    private String hashCourant;

    /**
     * Horodatage de l'entrée d'audit.
     * <p>
     * Correspond au moment exact où l'étape du pipeline a été exécutée.
     * Immuable après création.
     * </p>
     */
    @Column(name = "horodatage", nullable = false, updatable = false)
    private LocalDateTime horodatage;

    /**
     * Identifiant de l'opérateur ou du système ayant généré cette entrée.
     * <p>
     * Pour les actions automatiques du pipeline : "SYSTEME".
     * Pour les actions manuelles : email de l'utilisateur connecté.
     * </p>
     */
    @NotBlank(message = "L'opérateur est obligatoire")
    @Size(max = 150, message = "L'opérateur ne doit pas dépasser 150 caractères")
    @Column(name = "operateur", length = 150, nullable = false)
    @Builder.Default
    private String operateur = "SYSTEME";

    // Callbacks JPA

    /**
     * Initialise l'horodatage avant la première persistance.
     * <p>
     * Note : il n'y a pas de {@code @PreUpdate} car les entrées d'audit
     * sont immuables — une fois écrites, elles ne peuvent plus être modifiées.
     * </p>
     */
    @PrePersist
    protected void avantCreation() {
        this.horodatage = LocalDateTime.now();
        if (this.operateur == null) {
            this.operateur = "SYSTEME";
        }
    }

    // Constructeurs pratiques

    /**
     * Constructeur rapide pour créer une entrée d'audit avec les champs obligatoires.
     * Le hash est calculé séparément par {@link com.bna.flux.service.ServiceAudit}.
     *
     * @param transaction la transaction concernée
     * @param etape       l'étape du pipeline
     * @param action      l'action effectuée
     * @param detail      le détail JSON
     * @param operateur   l'opérateur (ou "SYSTEME")
     */
    public EntreeAudit(Transaction transaction, String etape, String action, String detail, String operateur) {
        this.transaction = transaction;
        this.etape = etape;
        this.action = action;
        this.detail = detail;
        this.operateur = operateur;
    }
}