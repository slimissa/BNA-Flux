package com.bna.flux.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception levée lorsqu'un code devise n'est pas reconnu ou n'est pas actif.
 * <p>
 * La validation de la devise est effectuée par {@link com.bna.flux.service.pipeline.etape.EtapeValidation}
 * lors du Stage 1 (Validation) du pipeline. Le code devise doit correspondre
 * à une devise active dans la table {@code devises}, initialisée au démarrage
 * par {@link com.bna.flux.service.InitialisateurDevises} à partir du fichier
 * {@code devises.json}.
 * </p>
 *
 * <p><b>Devises supportées par BNA :</b></p>
 * <ul>
 *   <li>TND (Dinar Tunisien) — 3 unités mineures (millimes)</li>
 *   <li>EUR, USD, GBP, CHF, CAD — 2 unités mineures</li>
 *   <li>KWD, BHD — 3 unités mineures</li>
 *   <li>JPY — 0 unité mineure</li>
 *   <li>SEK, NOK, DKK — 2 unités mineures</li>
 *   <li>SAR, QAR, AED — 2 unités mineures</li>
 *   <li>CNY — 2 unités mineures</li>
 *   <li>LYD (Livre Libyenne) — 3 unités mineures</li>
 * </ul>
 *
 * <p>Cette exception est interceptée par {@link GestionnaireGlobalExceptions}
 * et retourne une réponse HTTP 400 (BAD_REQUEST) avec le code {@code DEVISE_INCONNUE}
 * ou {@code DEVISE_INACTIVE} selon le cas.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Getter
public class DeviseInconnueException extends RuntimeException {

    /**
     * Le code devise fourni (3 lettres majuscules selon ISO 4217).
     */
    private final String codeDevise;

    /**
     * Indique si la devise existe mais est inactive (true) ou n'existe pas du tout (false).
     */
    private final boolean deviseInactive;

    /**
     * Crée une exception pour une devise inconnue.
     *
     * @param codeDevise le code devise non reconnu
     */
    public DeviseInconnueException(String codeDevise) {
        super("Le code devise '" + codeDevise + "' n'est pas reconnu. " +
              "Les devises acceptées sont : TND, EUR, KWD, USD, CAD, GBP, CHF, BHD, SEK, SAR, QAR, NOK, JPY, DKK, AED, CNY, LYD.");
        this.codeDevise = codeDevise;
        this.deviseInactive = false;
    }

    /**
     * Crée une exception pour une devise inactive.
     *
     * @param codeDevise le code devise inactif
     * @param inactive   doit être {@code true} pour ce constructeur
     */
    public DeviseInconnueException(String codeDevise, boolean inactive) {
        super("La devise '" + codeDevise + "' n'est plus active. " +
              "Veuillez utiliser une devise active pour les nouvelles transactions.");
        this.codeDevise = codeDevise;
        this.deviseInactive = inactive;
    }

    /**
     * Crée une exception pour une devise introuvable (constructeur statique).
     *
     * @param codeDevise le code devise recherché
     * @return l'exception
     */
    public static DeviseInconnueException introuvable(String codeDevise) {
        return new DeviseInconnueException(codeDevise);
    }

    /**
     * Crée une exception pour une devise inactive (constructeur statique).
     *
     * @param codeDevise le code devise inactif
     * @return l'exception
     */
    public static DeviseInconnueException inactive(String codeDevise) {
        return new DeviseInconnueException(codeDevise, true);
    }

    /**
     * Récupère les détails structurés de l'erreur pour la réponse API.
     *
     * @return une map contenant le code devise et son statut
     */
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("codeDevise", codeDevise);
        details.put("deviseInactive", deviseInactive);
        return details;
    }
}