package com.bna.flux.dto;

import com.bna.flux.entity.EtatDisjoncteur.Etat;
import com.bna.flux.entity.EtatDisjoncteur.TypeCible;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour la réponse après consultation ou modification d'un disjoncteur.
 * <p>
 * Retourné par {@link com.bna.flux.controller.DisjoncteurController} pour
 * la consultation de l'état des disjoncteurs et leur réinitialisation.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "id": 1,
 *         "nom": "Compte source 08601000191000748054",
 *         "typeCible": "COMPTE_SOURCE",
 *         "identifiantCible": "08601000191000748054",
 *         "etat": "OUVERT",
 *         "nombreEchecs": 3,
 *         "seuilEchecs": 3,
 *         "delaiOuvertureMinutes": 60,
 *         "fenetreHeures": 24,
 *         "dateDerniereOuverture": "2026-08-04T09:15:00",
 *         "dateDerniereFermeture": null,
 *         "dateDernierEchec": "2026-08-04T09:15:00",
 *         "peutEtreReinitialise": true,
 *         "tempsRestantAvantMiOuvert": 45
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
public class ReponseDisjoncteur {

    /**
     * Statut global de la réponse.
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Identifiant unique du disjoncteur.
     */
    private Long id;

    /**
     * Nom descriptif du disjoncteur.
     * <p>
     * Généré automatiquement à partir du type de cible et de l'identifiant.
     * Exemple : "Compte source 08601000191000748054".
     * </p>
     */
    private String nom;

    /**
     * Type de cible surveillée (COMPTE_SOURCE, COMPTE_DESTINATION, AGENCE, CANAL).
     */
    private TypeCible typeCible;

    /**
     * Libellé français du type de cible pour l'affichage.
     */
    private String typeCibleLabel;

    /**
     * Identifiant de la cible surveillée.
     * <p>
     * Format dépend du type : RIB 20 chiffres, code agence 3 chiffres,
     * ou nom du canal.
     * </p>
     */
    private String identifiantCible;

    /**
     * État actuel du disjoncteur (FERME, OUVERT, MI_OUVERT).
     */
    private Etat etat;

    /**
     * Libellé français de l'état pour l'affichage.
     */
    private String etatLabel;

    /**
     * Nombre d'échecs consécutifs enregistrés dans la fenêtre de temps.
     */
    private int nombreEchecs;

    /**
     * Seuil d'échecs à partir duquel le disjoncteur s'ouvre.
     */
    private int seuilEchecs;

    /**
     * Délai avant passage automatique en MI_OUVERT (en minutes).
     */
    private int delaiOuvertureMinutes;

    /**
     * Fenêtre de temps glissante pour le comptage des échecs (en heures).
     */
    private int fenetreHeures;

    /**
     * Date et heure de la dernière ouverture.
     */
    private LocalDateTime dateDerniereOuverture;

    /**
     * Date et heure de la dernière fermeture.
     */
    private LocalDateTime dateDerniereFermeture;

    /**
     * Date et heure du dernier échec enregistré.
     */
    private LocalDateTime dateDernierEchec;

    /**
     * Date de création du disjoncteur.
     */
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification.
     */
    private LocalDateTime dateModification;

    /**
     * Indique si le disjoncteur peut être réinitialisé manuellement.
     * <p>
     * Un disjoncteur peut être réinitialisé s'il est OUVERT ou MI_OUVERT.
     * La réinitialisation est réservée aux rôles SUPERVISEUR et ADMIN.
     * </p>
     */
    private boolean peutEtreReinitialise;

    /**
     * Temps restant avant le passage automatique en MI_OUVERT (en minutes).
     * <p>
     * Calculé uniquement si le disjoncteur est OUVERT.
     * Null si le disjoncteur est FERME ou MI_OUVERT.
     * </p>
     */
    private Long tempsRestantAvantMiOuvert;

    // Méthodes utilitaires

    /**
     * Résume un disjoncteur pour les listes.
     *
     * @param id    l'identifiant
     * @param nom   le nom
     * @param etat  l'état
     * @return une réponse résumée
     */
    public static ReponseDisjoncteur resumer(Long id, String nom, Etat etat) {
        return ReponseDisjoncteur.builder()
                .id(id)
                .nom(nom)
                .etat(etat)
                .peutEtreReinitialise(etat == Etat.OUVERT || etat == Etat.MI_OUVERT)
                .build();
    }
}