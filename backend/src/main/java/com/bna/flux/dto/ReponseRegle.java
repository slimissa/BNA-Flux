package com.bna.flux.dto;

import com.bna.flux.entity.Regle.Severite;
import com.bna.flux.entity.Regle.TypeRegle;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour la réponse après création, modification ou consultation d'une règle.
 * <p>
 * Retourné par {@link com.bna.flux.controller.RegleController} pour les
 * opérations CRUD sur les règles. Contient toutes les données de la règle
 * ainsi que des métadonnées utiles pour l'interface d'administration.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "id": 1,
 *         "nom": "Virement international > 50k TND",
 *         "description": "Surveille les virements sortants en devise étrangère",
 *         "expressionCondition": "montant >= 50000 AND codeDevise != 'TND'",
 *         "severite": "ELEVE",
 *         "contributionScore": 30,
 *         "typeRegle": "ALERTE",
 *         "categorie": "Virements internationaux",
 *         "priorite": 20,
 *         "actif": true,
 *         "dateCreation": "2026-08-04T10:00:00",
 *         "dateModification": null,
 *         "nombreDeclenchements": 15
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
public class ReponseRegle {

    /**
     * Statut global de la réponse.
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Identifiant unique de la règle.
     */
    private Long id;

    /**
     * Nom court et descriptif de la règle.
     */
    private String nom;

    /**
     * Description détaillée de la règle.
     */
    private String description;

    /**
     * Expression SpEL évaluée dynamiquement.
     */
    private String expressionCondition;

    /**
     * Niveau de sévérité (FAIBLE, MOYEN, ELEVE, CRITIQUE).
     */
    private Severite severite;

    /**
     * Label français de la sévérité pour l'affichage.
     */
    private String severiteLabel;

    /**
     * Contribution au score de risque si la règle est déclenchée (1-100).
     */
    private int contributionScore;

    /**
     * Type de règle (PREVENTION, ALERTE, AUTO_REJET).
     */
    private TypeRegle typeRegle;

    /**
     * Label français du type de règle pour l'affichage.
     */
    private String typeRegleLabel;

    /**
     * Catégorie fonctionnelle de la règle.
     */
    private String categorie;

    /**
     * Priorité d'évaluation (0 = maximale, 100 = minimale).
     */
    private int priorite;

    /**
     * Indique si la règle est active.
     */
    private boolean actif;

    /**
     * Label d'état pour l'affichage ("Active" / "Inactive").
     */
    private String etatLabel;

    /**
     * Date de création de la règle.
     */
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification.
     */
    private LocalDateTime dateModification;

    /**
     * Nombre de fois que cette règle a été déclenchée.
     * <p>
     * Récupéré depuis le repository {@link com.bna.flux.repository.AlerteRepository}
     * pour l'affichage dans le tableau de bord d'administration.
     * Null si non demandé (requête GET liste).
     * </p>
     */
    private Long nombreDeclenchements;

    // Méthodes utilitaires

    /**
     * Résume une règle pour les listes.
     *
     * @param id          l'identifiant
     * @param nom         le nom
     * @param severite    la sévérité
     * @param actif       l'état actif/inactif
     * @return une réponse résumée
     */
    public static ReponseRegle resumer(Long id, String nom, Severite severite, boolean actif) {
        return ReponseRegle.builder()
                .id(id)
                .nom(nom)
                .severite(severite)
                .actif(actif)
                .build();
    }
}