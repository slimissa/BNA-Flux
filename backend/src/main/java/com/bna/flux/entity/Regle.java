package com.bna.flux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant une règle de surveillance dans le moteur de règles BNA-FLUX.
 * <p>
 * Chaque règle est une expression SpEL (Spring Expression Language) évaluée
 * dynamiquement contre chaque transaction lors du passage dans le pipeline.
 * Les règles déclenchées génèrent des alertes et contribuent au score de risque.
 * </p>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Une règle active est évaluée pour chaque transaction au Stage 3 du pipeline.</li>
 *   <li>L'expression SpEL est compilée une fois puis mise en cache pour performance.</li>
 *   <li>La sévérité détermine l'impact sur le score de risque et le canal d'alerte.</li>
 *   <li>La contribution au score s'additionne pour toutes les règles déclenchées.</li>
 *   <li>Le type de règle détermine son comportement : blocage automatique ou simple alerte.</li>
 *   <li>Une règle désactivée est conservée en base pour l'historique mais n'est plus évaluée.</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>{@code Alerte} → {@code @ManyToOne Regle} : chaque alerte est liée à la
 *       règle qui l'a déclenchée.</li>
 * </ul>
 *
 * <p><b>Exemples d'expressions SpEL :</b></p>
 * <ul>
 *   <li>{@code montant >= 50000 AND codeDevise != 'TND'} — Virement international ≥ 50k</li>
 *   <li>{@code typeTransaction == 'ESPECES' AND montant >= 10000} — Dépôt espèces ≥ 10k</li>
 *   <li>{@code canal == 'EN_LIGNE' AND montant >= 5000 AND typeTransaction == 'VIREMENT'}
 *       — Virement en ligne ≥ 5k</li>
 *   <li>{@code paysOrigine != null AND paysOrigine != 'Tunisie' AND montant >= 20000}
 *       — Réception internationale ≥ 20k</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "regles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Regle {

    /**
     * Identifiant unique généré automatiquement.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom court et descriptif de la règle.
     * <p>
     * Utilisé dans l'interface d'administration et les rapports d'alerte.
     * Exemples : "Virement international > 50k TND", "Dépôt espèces suspect".
     * </p>
     */
    @NotBlank(message = "Le nom de la règle est obligatoire")
    @Size(max = 200, message = "Le nom ne doit pas dépasser 200 caractères")
    @Column(name = "nom", length = 200, nullable = false)
    private String nom;

    /**
     * Description détaillée de la règle.
     * <p>
     * Explique la logique métier, le contexte réglementaire éventuel,
     * et les raisons pour lesquelles cette règle existe.
     * </p>
     */
    @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Expression SpEL (Spring Expression Language) évaluée dynamiquement.
     * <p>
     * L'expression est compilée par {@link com.bna.flux.service.MoteurRegles}
     * et évaluée contre le {@link com.bna.flux.service.pipeline.ContextePipeline}
     * de chaque transaction.
     * </p>
     *
     * <p><b>Variables disponibles dans l'expression :</b></p>
     * <ul>
     *   <li>{@code montant} — le montant de la transaction (BigDecimal)</li>
     *   <li>{@code codeDevise} — le code ISO 4217 (String)</li>
     *   <li>{@code typeTransaction} — VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT</li>
     *   <li>{@code canal} — AGENCE, DAB, EN_LIGNE, MOBILE</li>
     *   <li>{@code paysOrigine} — pays d'origine après enrichissement</li>
     *   <li>{@code categorieContrepartie} — PARTICULIER, ENTREPRISE, GOUVERNEMENT</li>
     *   <li>{@code ribSource}, {@code ribDestination} — RIBs de la transaction</li>
     *   <li>{@code scoreRisque} — score actuel (pour les règles chaînées)</li>
     * </ul>
     *
     * <p><b>Opérateurs supportés :</b></p>
     * <ul>
     *   <li>Comparaison : {@code ==, !=, <, <=, >, >=}</li>
     *   <li>Logique : {@code AND, OR, NOT, !}</li>
     *   <li>Test de nullité : {@code != null, == null}</li>
     *   <li>Appartenance : {@code IN {'EUR', 'USD'}}</li>
     *   <li>Pattern matching : {@code matches '^[0-9]{20}$'}</li>
     *   <li>Arithmétique : {@code +, -, *, /}</li>
     * </ul>
     */
    @NotBlank(message = "L'expression est obligatoire")
    @Size(max = 500, message = "L'expression ne doit pas dépasser 500 caractères")
    @Column(name = "expression_condition", length = 500, nullable = false)
    private String expressionCondition;

    /**
     * Niveau de sévérité de la règle.
     * <p>
     * Détermine l'impact sur le score de risque et le mode de notification :
     * </p>
     * <ul>
     *   <li>{@code FAIBLE} — Information, contribution 5 points, dashboard uniquement</li>
     *   <li>{@code MOYEN} — Surveillance, contribution 15 points, dashboard + email groupé</li>
     *   <li>{@code ELEVE} — Alerte, contribution 30 points, dashboard + email groupé</li>
     *   <li>{@code CRITIQUE} — Blocage, contribution 50 points, dashboard + email immédiat</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "La sévérité est obligatoire")
    @Column(name = "severite", length = 10, nullable = false)
    @Builder.Default
    private Severite severite = Severite.MOYEN;

    /**
     * Contribution de cette règle au score de risque si elle est déclenchée.
     * <p>
     * Le score final est la somme des contributions de toutes les règles
     * déclenchées, plafonné à 100.
     * </p>
     * <p>
     * Valeurs recommandées par sévérité :
     * </p>
     * <ul>
     *   <li>FAIBLE : 5</li>
     *   <li>MOYEN : 15</li>
     *   <li>ELEVE : 30</li>
     *   <li>CRITIQUE : 50</li>
     * </ul>
     */
    @Min(value = 0, message = "La contribution au score ne peut pas être négative")
    @Max(value = 100, message = "La contribution au score ne peut pas dépasser 100")
    @Column(name = "contribution_score", nullable = false)
    @Builder.Default
    private int contributionScore = 15;

    /**
     * Type de règle déterminant son comportement lorsqu'elle est déclenchée.
     *
     * <ul>
     *   <li>{@code PREVENTION} — Génère une alerte mais ne bloque pas la transaction</li>
     *   <li>{@code ALERTE} — Génère une alerte et fait passer la transaction en SURVEILLE</li>
     *   <li>{@code AUTO_REJET} — Bloque automatiquement la transaction (BLOQUE)</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Le type de règle est obligatoire")
    @Column(name = "type_regle", length = 15, nullable = false)
    @Builder.Default
    private TypeRegle typeRegle = TypeRegle.ALERTE;

    /**
     * Catégorie fonctionnelle de la règle.
     * <p>
     * Permet d'organiser les règles dans l'interface d'administration
     * et de faciliter la recherche et le filtrage.
     * </p>
     */
    @Size(max = 100, message = "La catégorie ne doit pas dépasser 100 caractères")
    @Column(name = "categorie", length = 100)
    private String categorie;

    /**
     * Ordre de priorité d'évaluation.
     * <p>
     * Les règles sont évaluées par priorité décroissante (0 = priorité maximale).
     * Les règles CRITIQUE sont généralement évaluées en premier pour permettre
     * un blocage rapide.
     * </p>
     */
    @Min(value = 0, message = "La priorité ne peut pas être négative")
    @Max(value = 100, message = "La priorité ne peut pas dépasser 100")
    @Column(name = "priorite", nullable = false)
    @Builder.Default
    private int priorite = 50;

    /**
     * Indique si la règle est active et évaluée dans le pipeline.
     * <p>
     * Une règle inactive :
     * </p>
     * <ul>
     *   <li>N'est pas évaluée lors du passage des transactions dans le pipeline</li>
     *   <li>Reste en base pour l'historique des alertes passées</li>
     *   <li>Peut être réactivée à tout moment sans perdre sa configuration</li>
     * </ul>
     */
    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;

    /**
     * Date de création de la règle.
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification de la règle.
     */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    // Callbacks JPA
    @PrePersist
    protected void avantCreation() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void avantModification() {
        this.dateModification = LocalDateTime.now();
    }

    // Enums internes
    /**
     * Niveaux de sévérité d'une règle.
     */
    public enum Severite {
        /**
         * Information — faible impact, dashboard uniquement.
         */
        FAIBLE,

        /**
         * Surveillance — impact modéré, dashboard + email groupé.
         */
        MOYEN,

        /**
         * Alerte — impact important, dashboard + email groupé.
         */
        ELEVE,

        /**
         * Critique — impact maximal, blocage possible, dashboard + email immédiat.
         */
        CRITIQUE
    }

    /**
     * Types de règle déterminant le comportement en cas de déclenchement.
     */
    public enum TypeRegle {
        /**
         * Génère une alerte sans bloquer la transaction.
         */
        PREVENTION,

        /**
         * Génère une alerte et place la transaction en statut SURVEILLE.
         */
        ALERTE,

        /**
         * Bloque automatiquement la transaction (statut BLOQUE).
         */
        AUTO_REJET
    }

    // Méthodes métier

    /**
     * Vérifie si cette règle doit être évaluée en priorité (règle critique
     * ou de type auto-rejet).
     *
     * @return {@code true} si la règle est prioritaire
     */
    public boolean estPrioritaire() {
        return severite == Severite.CRITIQUE || typeRegle == TypeRegle.AUTO_REJET;
    }

    /**
     * Active la règle pour qu'elle soit évaluée dans le pipeline.
     */
    public void activer() {
        this.actif = true;
    }

    /**
     * Désactive la règle sans la supprimer de la base.
     */
    public void desactiver() {
        this.actif = false;
    }

    /**
     * Bascule l'état actif/inactif de la règle.
     *
     * @return le nouvel état après basculement
     */
    public boolean basculer() {
        this.actif = !this.actif;
        return this.actif;
    }
}