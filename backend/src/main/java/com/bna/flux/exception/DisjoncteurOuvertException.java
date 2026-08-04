package com.bna.flux.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception levée lorsqu'une transaction est bloquée par un disjoncteur ouvert.
 * <p>
 * Le disjoncteur (Circuit Breaker) est un mécanisme de protection automatique
 * géré par {@link com.bna.flux.service.ServiceDisjoncteur}. Lorsqu'un nombre
 * anormal de transactions bloquées est détecté pour une cible (compte, agence,
 * canal), le disjoncteur s'ouvre et bloque toutes les transactions suivantes
 * pour cette cible jusqu'à ce que la situation soit résolue.
 * </p>
 *
 * <p><b>Cycle de vie du disjoncteur :</b></p>
 * <pre>
 * FERMÉ → (nb échecs ≥ seuil) → OUVERT → (délai écoulé) → MI_OUVERT → (test) → FERMÉ ou OUVERT
 * </pre>
 *
 * <p>Cette exception est interceptée par {@link GestionnaireGlobalExceptions}
 * et retourne une réponse HTTP 422 (UNPROCESSABLE_ENTITY) avec le code
 * {@code DISJONCTEUR_OUVERT}. Le statut 422 est utilisé (plutôt que 400 ou 403)
 * car la requête est syntaxiquement correcte mais ne peut pas être traitée
 * dans l'état actuel du système.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Getter
public class DisjoncteurOuvertException extends RuntimeException {

    /**
     * Type de cible du disjoncteur (COMPTE_SOURCE, COMPTE_DESTINATION, AGENCE, CANAL).
     */
    private final String typeCible;

    /**
     * Identifiant de la cible (RIB, code agence, ou nom du canal).
     */
    private final String identifiantCible;

    /**
     * Date depuis laquelle le disjoncteur est ouvert.
     */
    private final LocalDateTime dateOuverture;

    /**
     * Nombre de minutes avant le passage automatique en MI_OUVERT.
     */
    private final Long minutesAvantTest;

    /**
     * Crée une exception pour une transaction bloquée par un disjoncteur ouvert.
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     */
    public DisjoncteurOuvertException(String typeCible, String identifiantCible) {
        super("Le circuit breaker est ouvert pour la cible " + typeCible + " : " + identifiantCible +
              ". La transaction est automatiquement bloquée.");
        this.typeCible = typeCible;
        this.identifiantCible = identifiantCible;
        this.dateOuverture = null;
        this.minutesAvantTest = null;
    }

    /**
     * Crée une exception avec la date d'ouverture et le temps restant.
     *
     * @param typeCible          le type de cible
     * @param identifiantCible   l'identifiant de la cible
     * @param dateOuverture      la date d'ouverture du disjoncteur
     * @param minutesAvantTest   le nombre de minutes avant passage en MI_OUVERT
     */
    public DisjoncteurOuvertException(String typeCible, String identifiantCible,
                                       LocalDateTime dateOuverture, Long minutesAvantTest) {
        super("Le circuit breaker est ouvert pour " + typeCible + " : " + identifiantCible +
              " depuis " + dateOuverture +
              (minutesAvantTest != null ? " (test automatique dans " + minutesAvantTest + " minutes)" : ""));
        this.typeCible = typeCible;
        this.identifiantCible = identifiantCible;
        this.dateOuverture = dateOuverture;
        this.minutesAvantTest = minutesAvantTest;
    }

    /**
     * Récupère les détails structurés de l'erreur pour la réponse API.
     *
     * @return une map contenant les informations du disjoncteur
     */
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("typeCible", typeCible);
        details.put("identifiantCible", identifiantCible);
        details.put("estOuvert", true);
        if (dateOuverture != null) {
            details.put("dateOuverture", dateOuverture.toString());
        }
        if (minutesAvantTest != null) {
            details.put("minutesAvantTest", minutesAvantTest);
        }
        return details;
    }
}