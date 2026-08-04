package com.bna.flux.dto;

import com.bna.flux.entity.Alerte.NiveauAlerte;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour la réponse après consultation d'une alerte.
 * <p>
 * Retourné par {@link com.bna.flux.controller.AlerteController} et inclus
 * dans {@link ReponseTransaction} pour la liste des alertes liées à une
 * transaction.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "id": 567,
 *         "transactionId": 1234,
 *         "referenceTransaction": "BNA-20260804-0001",
 *         "regleId": 1,
 *         "nomRegle": "Virement international > 50k TND",
 *         "message": "Virement international > 50k TND — Transaction de 75 000,00 EUR vers FR763000...",
 *         "niveau": "ELEVE",
 *         "dateCreation": "2026-08-04T09:15:01",
 *         "acquittee": false,
 *         "acquitteePar": null,
 *         "acquitteeLe": null,
 *         "emailEnvoye": false,
 *         "delaiMinutes": 5
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
public class ReponseAlerte {

    /**
     * Statut global de la réponse.
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Identifiant unique de l'alerte.
     */
    private Long id;

    /**
     * Identifiant de la transaction ayant déclenché l'alerte.
     */
    private Long transactionId;

    /**
     * Référence de la transaction (format BNA-YYYYMMDD-XXXX).
     * <p>
     * Incluse pour faciliter la navigation dans l'interface.
     * </p>
     */
    private String referenceTransaction;

    /**
     * Identifiant de la règle qui a été déclenchée.
     */
    private Long regleId;

    /**
     * Nom de la règle déclenchée.
     * <p>
     * Inclus pour l'affichage sans nécessiter une requête supplémentaire.
     * </p>
     */
    private String nomRegle;

    /**
     * Message descriptif de l'alerte.
     * <p>
     * Généré automatiquement à partir du nom de la règle et des détails
     * de la transaction.
     * </p>
     */
    private String message;

    /**
     * Niveau de sévérité de l'alerte.
     */
    private NiveauAlerte niveau;

    /**
     * Date et heure de déclenchement de l'alerte.
     */
    private LocalDateTime dateCreation;

    /**
     * Indique si l'alerte a été acquittée par un opérateur.
     */
    private boolean acquittee;

    /**
     * Identifiant de l'opérateur ayant acquitté l'alerte.
     */
    private String acquitteePar;

    /**
     * Date et heure de l'acquittement.
     */
    private LocalDateTime acquitteeLe;

    /**
     * Indique si un email a été envoyé pour cette alerte.
     */
    private boolean emailEnvoye;

    /**
     * Date d'envoi de l'email.
     */
    private LocalDateTime emailEnvoyeLe;

    /**
     * Destinataire de l'email.
     */
    private String emailDestinataire;

    /**
     * Délai écoulé depuis le déclenchement (en minutes).
     * <p>
     * Calculé côté serveur pour faciliter l'affichage du temps écoulé
     * dans l'interface ("il y a 5 min", "il y a 2h").
     * </p>
     */
    private Long delaiMinutes;

    // Méthodes utilitaires

    /**
     * Résume une alerte pour les listes.
     *
     * @param id          l'identifiant de l'alerte
     * @param nomRegle    le nom de la règle déclenchée
     * @param niveau      le niveau de sévérité
     * @param acquittee   si l'alerte est acquittée
     * @return une réponse résumée
     */
    public static ReponseAlerte resumer(Long id, String nomRegle, NiveauAlerte niveau, boolean acquittee) {
        return ReponseAlerte.builder()
                .id(id)
                .nomRegle(nomRegle)
                .niveau(niveau)
                .acquittee(acquittee)
                .build();
    }
}