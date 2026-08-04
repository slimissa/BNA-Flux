package com.bna.flux.dto;

import com.bna.flux.entity.Regle.Severite;
import com.bna.flux.entity.Regle.TypeRegle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la création ou modification d'une règle de surveillance.
 * <p>
 * Reçu par {@link com.bna.flux.controller.RegleController} lors de la
 * création (POST) ou modification (PUT) d'une règle. L'expression SpEL
 * est validée syntaxiquement avant d'être sauvegardée.
 * </p>
 *
 * <p><b>Validation :</b></p>
 * <ul>
 *   <li>Le nom doit être unique et descriptif.</li>
 *   <li>L'expression SpEL doit être syntaxiquement valide (validée par
 *       {@link com.bna.flux.service.MoteurRegles} avant sauvegarde).</li>
 *   <li>La contribution au score doit être cohérente avec la sévérité.</li>
 *   <li>La priorité doit être entre 0 (maximale) et 100 (minimale).</li>
 * </ul>
 *
 * <p><b>Exemples de requêtes :</b></p>
 * <pre>
 * // Règle de prévention simple
 * {
 *     "nom": "Surveillance virements internationaux",
 *     "description": "Surveille les virements sortants en devise étrangère",
 *     "expressionCondition": "montant >= 50000 AND codeDevise != 'TND'",
 *     "severite": "ELEVE",
 *     "contributionScore": 30,
 *     "typeRegle": "ALERTE",
 *     "categorie": "Virements internationaux",
 *     "priorite": 20
 * }
 *
 * // Règle d'auto-rejet
 * {
 *     "nom": "Blocage dépôts espèces suspects",
 *     "description": "Bloque les dépôts en espèces supérieurs à 10k TND hors agence",
 *     "expressionCondition": "typeTransaction == 'ESPECES' AND montant >= 10000 AND canal != 'AGENCE'",
 *     "severite": "CRITIQUE",
 *     "contributionScore": 50,
 *     "typeRegle": "AUTO_REJET",
 *     "categorie": "Lutte anti-blanchiment",
 *     "priorite": 5
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
public class RequeteRegle {

    /**
     * Nom court et descriptif de la règle.
     * <p>
     * Doit être unique. Utilisé dans l'interface d'administration
     * et les rapports d'alerte.
     * </p>
     */
    @NotBlank(message = "Le nom de la règle est obligatoire")
    @Size(min = 5, max = 200, message = "Le nom doit contenir entre 5 et 200 caractères")
    private String nom;

    /**
     * Description détaillée de la règle.
     * <p>
     * Explique la logique métier, le contexte réglementaire,
     * et les raisons de son existence. Champ optionnel mais recommandé.
     * </p>
     */
    @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
    private String description;

    /**
     * Expression SpEL évaluée dynamiquement contre chaque transaction.
     * <p>
     * L'expression est compilée et validée syntaxiquement avant sauvegarde.
     * Les variables disponibles sont documentées dans l'entité {@link com.bna.flux.entity.Regle}.
     * </p>
     *
     * <p><b>Exemples valides :</b></p>
     * <ul>
     *   <li>{@code montant >= 50000 AND codeDevise != 'TND'}</li>
     *   <li>{@code typeTransaction == 'ESPECES' AND montant >= 10000}</li>
     *   <li>{@code canal == 'EN_LIGNE' AND montant >= 5000 AND typeTransaction == 'VIREMENT'}</li>
     *   <li>{@code paysOrigine != null AND paysOrigine != 'Tunisie'}</li>
     * </ul>
     */
    @NotBlank(message = "L'expression de la règle est obligatoire")
    @Size(min = 3, max = 500, message = "L'expression doit contenir entre 3 et 500 caractères")
    private String expressionCondition;

    /**
     * Niveau de sévérité de la règle.
     * <p>
     * Détermine l'impact sur le score et le mode de notification :
     * </p>
     * <ul>
     *   <li>FAIBLE — Information, contribution 5 pts, dashboard uniquement</li>
     *   <li>MOYEN — Surveillance, contribution 15 pts, dashboard + email groupé</li>
     *   <li>ELEVE — Alerte, contribution 30 pts, dashboard + email groupé</li>
     *   <li>CRITIQUE — Blocage, contribution 50 pts, dashboard + email immédiat</li>
     * </ul>
     */
    @NotNull(message = "La sévérité est obligatoire")
    private Severite severite;

    /**
     * Contribution de cette règle au score de risque si déclenchée.
     * <p>
     * Le score final est la somme des contributions de toutes les règles
     * déclenchées, plafonné à 100.
     * </p>
     */
    @Min(value = 1, message = "La contribution au score doit être au moins 1")
    @Max(value = 100, message = "La contribution au score ne peut pas dépasser 100")
    private int contributionScore;

    /**
     * Type de règle déterminant son comportement lorsqu'elle est déclenchée.
     * <ul>
     *   <li>PREVENTION — Génère une alerte sans bloquer</li>
     *   <li>ALERTE — Génère une alerte et place en SURVEILLE</li>
     *   <li>AUTO_REJET — Bloque automatiquement la transaction</li>
     * </ul>
     */
    @NotNull(message = "Le type de règle est obligatoire")
    private TypeRegle typeRegle;

    /**
     * Catégorie fonctionnelle de la règle (optionnel).
     * <p>
     * Permet d'organiser les règles dans l'interface d'administration.
     * Exemples : "Virements internationaux", "Lutte anti-blanchiment",
     * "Sécurité des canaux en ligne".
     * </p>
     */
    @Size(max = 100, message = "La catégorie ne doit pas dépasser 100 caractères")
    private String categorie;

    /**
     * Priorité d'évaluation (0 = priorité maximale, 100 = priorité minimale).
     * <p>
     * Les règles CRITIQUE et AUTO_REJET devraient avoir une priorité basse
     * (proche de 0) pour être évaluées en premier.
     * </p>
     */
    @Min(value = 0, message = "La priorité ne peut pas être négative")
    @Max(value = 100, message = "La priorité ne peut pas dépasser 100")
    @Builder.Default
    private int priorite = 50;

    /**
     * Indique si la règle doit être active immédiatement après création.
     * <p>
     * Par défaut, une règle est créée active. Mettre à false pour créer
     * une règle en mode brouillon.
     * </p>
     */
    @Builder.Default
    private boolean actif = true;
}