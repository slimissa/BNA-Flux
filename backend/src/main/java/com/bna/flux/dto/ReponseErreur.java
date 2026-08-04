package com.bna.flux.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO pour les réponses d'erreur standardisées de l'API BNA-FLUX.
 * <p>
 * Toute erreur (validation, métier, technique) produit une réponse
 * cohérente avec ce format, facilitant le traitement côté frontend.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "ERREUR",
 *     "code": "RIB_INVALIDE",
 *     "message": "Le RIB source 12345678901234567890 est invalide. La clé de contrôle ne correspond pas.",
 *     "horodatage": "2026-08-04T14:30:00+01:00",
 *     "details": {
 *         "champ": "ribSource",
 *         "valeurFournie": "12345678901234567890",
 *         "cleAttendue": "45",
 *         "cleCalculee": "12"
 *     }
 * }
 * </pre>
 *
 * <p><b>Codes d'erreur standard :</b></p>
 * <table border="1">
 *   <tr><th>Code</th><th>HTTP</th><th>Message</th></tr>
 *   <tr><td>RIB_INVALIDE</td><td>400</td><td>Le RIB fourni est invalide</td></tr>
 *   <tr><td>DEVISE_INCONNUE</td><td>400</td><td>Le code devise n'est pas reconnu</td></tr>
 *   <tr><td>DEVISE_INACTIVE</td><td>400</td><td>La devise n'est plus utilisable</td></tr>
 *   <tr><td>MONTANT_INVALIDE</td><td>400</td><td>Le montant doit être supérieur à zéro</td></tr>
 *   <tr><td>REGLE_SYNTAXE_INVALIDE</td><td>400</td><td>L'expression de la règle contient une erreur</td></tr>
 *   <tr><td>REGLE_DUPLIQUEE</td><td>409</td><td>Une règle avec ce nom existe déjà</td></tr>
 *   <tr><td>DISJONCTEUR_OUVERT</td><td>422</td><td>Le circuit breaker est ouvert pour cette cible</td></tr>
 *   <tr><td>TRANSACTION_INTROUVABLE</td><td>404</td><td>Transaction non trouvée</td></tr>
 *   <tr><td>REGLE_INTROUVABLE</td><td>404</td><td>Règle non trouvée</td></tr>
 *   <tr><td>UTILISATEUR_INTROUVABLE</td><td>404</td><td>Utilisateur non trouvé</td></tr>
 *   <tr><td>ACCES_REFUSE</td><td>403</td><td>Vous n'avez pas les droits pour cette opération</td></tr>
 *   <tr><td>AUTHENTIFICATION_ECHOUEE</td><td>401</td><td>Email ou mot de passe incorrect</td></tr>
 *   <tr><td>JETON_EXPIRE</td><td>401</td><td>Votre session a expiré, veuillez vous reconnecter</td></tr>
 *   <tr><td>JETON_INVALIDE</td><td>401</td><td>Token d'authentification invalide</td></tr>
 *   <tr><td>ERREUR_INTERNE</td><td>500</td><td>Une erreur interne est survenue</td></tr>
 *   <tr><td>VALIDATION_ECHOUEE</td><td>400</td><td>Erreur de validation des données</td></tr>
 * </table>
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
public class ReponseErreur {

    /**
     * Statut de la réponse.
     * <p>
     * Toujours "ERREUR" pour les réponses d'erreur.
     * </p>
     */
    @Builder.Default
    private String statut = "ERREUR";

    /**
     * Code d'erreur unique identifiant le type d'erreur.
     * <p>
     * Format : MAJUSCULES_AVEC_UNDERSCORES.
     * Utilisé par le frontend pour la traduction et le traitement conditionnel.
     * </p>
     */
    private String code;

    /**
     * Message d'erreur en français, destiné à l'affichage.
     * <p>
     * Clair et actionnable. Exemple : "Le RIB source est invalide. La clé de
     * contrôle ne correspond pas. Valeur attendue : 54, valeur calculée : 12."
     * </p>
     */
    private String message;

    /**
     * Horodatage de l'erreur au format ISO 8601.
     */
    @Builder.Default
    private LocalDateTime horodatage = LocalDateTime.now();

    /**
     * Détails supplémentaires sur l'erreur (optionnel).
     * <p>
     * Contient des informations structurées sur les champs en erreur,
     * les valeurs attendues vs fournies, etc.
     * </p>
     * <p>
     * Exemples :
     * </p>
     * <ul>
     *   <li>Erreur de validation : {"champ": "email", "valeur": "xxx", "raison": "Format invalide"}</li>
     *   <li>Erreur RIB : {"champ": "ribSource", "cleAttendue": "54", "cleCalculee": "12"}</li>
     *   <li>Erreur disjoncteur : {"typeCible": "COMPTE_SOURCE", "identifiantCible": "08601..."}</li>
     * </ul>
     */
    private Map<String, Object> details;

    /**
     * Chemin de la requête ayant causé l'erreur.
     * <p>
     * Utile pour le débogage. Exemple : "/api/transactions".
     * </p>
     */
    private String chemin;

    // Constructeurs statiques pratiques

    /**
     * Crée une réponse d'erreur simple (code + message).
     *
     * @param code    le code d'erreur
     * @param message le message descriptif
     * @return la réponse d'erreur
     */
    public static ReponseErreur of(String code, String message) {
        return ReponseErreur.builder()
                .statut("ERREUR")
                .code(code)
                .message(message)
                .horodatage(LocalDateTime.now())
                .build();
    }

    /**
     * Crée une réponse d'erreur avec détails.
     *
     * @param code    le code d'erreur
     * @param message le message descriptif
     * @param details les détails structurés
     * @return la réponse d'erreur
     */
    public static ReponseErreur of(String code, String message, Map<String, Object> details) {
        return ReponseErreur.builder()
                .statut("ERREUR")
                .code(code)
                .message(message)
                .details(details)
                .horodatage(LocalDateTime.now())
                .build();
    }

    /**
     * Crée une réponse d'erreur avec détails et chemin.
     *
     * @param code    le code d'erreur
     * @param message le message descriptif
     * @param details les détails structurés
     * @param chemin  le chemin de la requête
     * @return la réponse d'erreur
     */
    public static ReponseErreur of(String code, String message, Map<String, Object> details, String chemin) {
        return ReponseErreur.builder()
                .statut("ERREUR")
                .code(code)
                .message(message)
                .details(details)
                .chemin(chemin)
                .horodatage(LocalDateTime.now())
                .build();
    }

    /**
     * Crée une réponse pour une erreur de validation générique.
     *
     * @param message le message de validation
     * @return la réponse d'erreur
     */
    public static ReponseErreur validation(String message) {
        return of("VALIDATION_ECHOUEE", message);
    }

    /**
     * Crée une réponse pour une ressource non trouvée.
     *
     * @param ressource le type de ressource (ex: "Transaction", "Règle")
     * @param id        l'identifiant recherché
     * @return la réponse d'erreur
     */
    public static ReponseErreur introuvable(String ressource, Object id) {
        return of(
                ressource.toUpperCase() + "_INTROUVABLE",
                ressource + " non trouvé(e) avec l'identifiant : " + id
        );
    }

    /**
     * Crée une réponse pour une erreur interne.
     *
     * @param message le message technique (non exposé en production)
     * @return la réponse d'erreur
     */
    public static ReponseErreur interne(String message) {
        return of("ERREUR_INTERNE", "Une erreur interne est survenue. Veuillez contacter le support technique.");
    }
}