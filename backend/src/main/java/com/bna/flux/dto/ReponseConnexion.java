package com.bna.flux.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la réponse d'authentification réussie.
 * <p>
 * Retourné par {@link com.bna.flux.controller.AuthController#connexion(RequeteConnexion)}
 * après une authentification email/mot de passe réussie. Contient les tokens JWT
 * et les informations de base de l'utilisateur connecté.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "tokenAcces": "eyJhbGciOiJIUzI1NiJ9...",
 *     "tokenRafraichissement": "eyJhbGciOiJIUzI1NiJ9...",
 *     "typeToken": "Bearer",
 *     "expireDans": 3600,
 *     "utilisateur": {
 *         "email": "ahmed.bensalah@bna.com.tn",
 *         "nom": "Ahmed Ben Salah",
 *         "role": "OPERATEUR",
 *         "agence": "601"
 *     }
 * }
 * </pre>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Les tokens sont signés avec HMAC-SHA256.</li>
 *   <li>Le token de rafraîchissement a une durée de vie plus longue (24h)
 *       et contient moins de claims que le token d'accès.</li>
 *   <li>Le mot de passe n'est JAMAIS inclus dans la réponse.</li>
 *   <li>Le champ {@code motDePasse} de l'utilisateur est exclu de la
 *       sérialisation JSON.</li>
 * </ul>
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
public class ReponseConnexion {

    /**
     * Statut de la réponse.
     * <p>
     * Toujours "SUCCES" pour une authentification réussie.
     * En cas d'échec, c'est {@link ReponseErreur} qui est retourné.
     * </p>
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Token d'accès JWT.
     * <p>
     * Durée de validité : configurable (défaut 60 minutes).
     * Doit être inclus dans le header {@code Authorization: Bearer <token>}
     * pour toutes les requêtes protégées.
     * </p>
     */
    private String tokenAcces;

    /**
     * Token de rafraîchissement JWT.
     * <p>
     * Durée de validité : configurable (défaut 24 heures).
     * Utilisé pour obtenir un nouveau token d'accès sans ré-authentification.
     * </p>
     */
    private String tokenRafraichissement;

    /**
     * Type de token pour le header Authorization.
     * <p>
     * Toujours "Bearer" — le client doit préfixer le token avec cette valeur.
     * </p>
     */
    @Builder.Default
    private String typeToken = "Bearer";

    /**
     * Durée de validité du token d'accès en secondes.
     * <p>
     * Permet au frontend de planifier le rafraîchissement avant expiration.
     * </p>
     */
    private long expireDans;

    /**
     * Informations de base sur l'utilisateur connecté.
     * <p>
     * Inclut l'email, le nom, le rôle et le code agence.
     * Utilisé par le frontend pour afficher le nom de l'utilisateur
     * et adapter l'interface selon le rôle.
     * </p>
     */
    private UtilisateurInfo utilisateur;

    // Classe interne — Informations utilisateur

    /**
     * Sous-DTO contenant les informations non sensibles de l'utilisateur.
     * <p>
     * Extrait de l'entité {@link com.bna.flux.entity.Utilisateur} pour
     * ne pas exposer le mot de passe hashé ou d'autres données internes.
     * </p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UtilisateurInfo {

        /**
         * Email de l'utilisateur (identifiant de connexion).
         */
        private String email;

        /**
         * Nom complet de l'utilisateur pour l'affichage.
         */
        private String nom;

        /**
         * Rôle de l'utilisateur (OPERATEUR, SUPERVISEUR, ADMIN).
         * <p>
         * Utilisé par le frontend pour le contrôle d'accès côté client
         * (affichage/masquage des boutons et menus).
         * </p>
         */
        private String role;

        /**
         * Code agence de l'utilisateur.
         * <p>
         * Null pour les ADMIN qui ont accès à toutes les agences.
         * </p>
         */
        private String agence;
    }

    // Méthodes utilitaires

    /**
     * Crée une réponse de connexion complète.
     *
     * @param tokenAcces             le token d'accès JWT
     * @param tokenRafraichissement  le token de rafraîchissement JWT
     * @param dureeAccesSecondes     la durée de validité du token d'accès en secondes
     * @param email                  l'email de l'utilisateur
     * @param nom                    le nom de l'utilisateur
     * @param role                   le rôle de l'utilisateur
     * @param agence                 le code agence (peut être null)
     * @return la réponse de connexion construite
     */
    public static ReponseConnexion of(String tokenAcces, String tokenRafraichissement,
                                       long dureeAccesSecondes, String email,
                                       String nom, String role, String agence) {
        return ReponseConnexion.builder()
                .statut("SUCCES")
                .tokenAcces(tokenAcces)
                .tokenRafraichissement(tokenRafraichissement)
                .typeToken("Bearer")
                .expireDans(dureeAccesSecondes)
                .utilisateur(UtilisateurInfo.builder()
                        .email(email)
                        .nom(nom)
                        .role(role)
                        .agence(agence)
                        .build())
                .build();
    }
}