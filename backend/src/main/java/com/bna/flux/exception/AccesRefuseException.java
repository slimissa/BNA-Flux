package com.bna.flux.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception levée lorsqu'un utilisateur tente d'accéder à une ressource
 * pour laquelle il ne dispose pas des droits suffisants.
 * <p>
 * Cette exception complète le mécanisme standard de Spring Security
 * ({@link org.springframework.security.access.AccessDeniedException})
 * en fournissant un message métier en français et un contexte plus riche
 * sur l'opération refusée.
 * </p>
 *
 * <p><b>Rôles et permissions dans BNA-FLUX :</b></p>
 * <table border="1">
 *   <tr><th>Rôle</th><th>Permissions</th></tr>
 *   <tr><td>OPERATEUR</td><td>Consultation transactions/alertes (agence), acquittement alertes</td></tr>
 *   <tr><td>SUPERVISEUR</td><td>OPERATEUR + CRUD règles, réinitialisation disjoncteurs</td></tr>
 *   <tr><td>ADMIN</td><td>Tous droits, toutes agences, gestion utilisateurs</td></tr>
 * </table>
 *
 * <p>Cette exception est interceptée par {@link GestionnaireGlobalExceptions}
 * et retourne une réponse HTTP 403 (FORBIDDEN) avec le code {@code ACCES_REFUSE}.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Getter
public class AccesRefuseException extends RuntimeException {

    /**
     * L'action que l'utilisateur tentait d'effectuer.
     * <p>
     * Exemples : "créer une règle", "réinitialiser un disjoncteur",
     * "accéder aux données d'une autre agence", "gérer les utilisateurs".
     * </p>
     */
    private final String action;

    /**
     * Le rôle requis pour effectuer cette action.
     * <p>
     * Exemples : "SUPERVISEUR", "ADMIN".
     * </p>
     */
    private final String roleRequis;

    /**
     * Le rôle actuel de l'utilisateur.
     */
    private final String roleActuel;

    /**
     * Crée une exception avec l'action tentée et le rôle requis.
     *
     * @param action     l'action que l'utilisateur tentait d'effectuer
     * @param roleRequis le rôle minimum requis
     */
    public AccesRefuseException(String action, String roleRequis) {
        super("Accès refusé : vous devez être " + roleRequis + " pour " + action + ".");
        this.action = action;
        this.roleRequis = roleRequis;
        this.roleActuel = null;
    }

    /**
     * Crée une exception avec l'action, le rôle requis et le rôle actuel.
     *
     * @param action      l'action tentée
     * @param roleRequis  le rôle requis
     * @param roleActuel  le rôle actuel de l'utilisateur
     */
    public AccesRefuseException(String action, String roleRequis, String roleActuel) {
        super("Accès refusé : l'action '" + action + "' nécessite le rôle " + roleRequis +
              ". Votre rôle actuel est " + roleActuel + ".");
        this.action = action;
        this.roleRequis = roleRequis;
        this.roleActuel = roleActuel;
    }

    /**
     * Crée une exception pour un accès inter-agence refusé.
     *
     * @param agenceDemandee le code agence demandé
     * @param agenceUtilisateur le code agence de l'utilisateur
     * @return l'exception
     */
    public static AccesRefuseException agenceNonAutorisee(String agenceDemandee, String agenceUtilisateur) {
        return new AccesRefuseException(
                "consulter les données de l'agence " + agenceDemandee,
                "APPARTENIR A L'AGENCE " + agenceDemandee + " ou être ADMIN",
                "AGENCE " + agenceUtilisateur
        );
    }

    /**
     * Crée une exception pour la création/modification de règle refusée.
     *
     * @param roleActuel le rôle actuel de l'utilisateur
     * @return l'exception
     */
    public static AccesRefuseException gestionReglesRefusee(String roleActuel) {
        return new AccesRefuseException("gérer les règles de surveillance", "SUPERVISEUR ou ADMIN", roleActuel);
    }

    /**
     * Crée une exception pour la réinitialisation de disjoncteur refusée.
     *
     * @param roleActuel le rôle actuel de l'utilisateur
     * @return l'exception
     */
    public static AccesRefuseException reinitialisationDisjoncteurRefusee(String roleActuel) {
        return new AccesRefuseException("réinitialiser un disjoncteur", "SUPERVISEUR ou ADMIN", roleActuel);
    }

    /**
     * Crée une exception pour la gestion des utilisateurs refusée.
     *
     * @param roleActuel le rôle actuel de l'utilisateur
     * @return l'exception
     */
    public static AccesRefuseException gestionUtilisateursRefusee(String roleActuel) {
        return new AccesRefuseException("gérer les utilisateurs", "ADMIN", roleActuel);
    }

    /**
     * Récupère les détails structurés de l'erreur pour la réponse API.
     *
     * @return une map contenant les informations de l'erreur d'accès
     */
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("action", action);
        details.put("roleRequis", roleRequis);
        if (roleActuel != null) {
            details.put("roleActuel", roleActuel);
        }
        return details;
    }
}