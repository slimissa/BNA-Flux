package com.bna.flux.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception levée lorsqu'un RIB tunisien est invalide.
 * <p>
 * La validation du RIB est effectuée par {@link com.bna.flux.service.ValidateurRib}
 * lors du Stage 1 (Validation) du pipeline. L'algorithme de vérification utilise
 * le modulo 97 : {@code clé = 97 - (N × 100 mod 97)} où N est le nombre formé
 * par les 18 premiers chiffres du RIB.
 * </p>
 *
 * <p><b>Format du RIB tunisien :</b></p>
 * <ul>
 *   <li>20 chiffres au total</li>
 *   <li>Code banque : 2 chiffres (positions 1-2)</li>
 *   <li>Code agence : 3 chiffres (positions 3-5)</li>
 *   <li>Numéro compte : 13 chiffres (positions 6-18)</li>
 *   <li>Clé RIB : 2 chiffres (positions 19-20)</li>
 * </ul>
 *
 * <p><b>Exemple valide :</b> {@code 08601000191000748054} (clé = 54)</p>
 *
 * <p>Cette exception est interceptée par {@link GestionnaireGlobalExceptions}
 * et retourne une réponse HTTP 400 (BAD_REQUEST) avec le code {@code RIB_INVALIDE}.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Getter
public class RibInvalideException extends RuntimeException {

    /**
     * Le numéro RIB fourni (20 chiffres).
     */
    private final String rib;

    /**
     * Indique si le RIB source (émetteur) ou destination (bénéficiaire) est invalide.
     */
    private final String typeRib;

    /**
     * Détails structurés de l'erreur pour la réponse API.
     */
    private final Map<String, Object> details;

    /**
     * Crée une exception pour un RIB invalide avec la raison.
     *
     * @param rib     le RIB invalide
     * @param typeRib "SOURCE" ou "DESTINATION"
     * @param raison  la raison de l'invalidité
     */
    public RibInvalideException(String rib, String typeRib, String raison) {
        super("Le RIB " + typeRib.toLowerCase() + " " + rib + " est invalide : " + raison);
        this.rib = rib;
        this.typeRib = typeRib;
        this.details = new HashMap<>();
        this.details.put("rib", rib);
        this.details.put("type", typeRib);
        this.details.put("raison", raison);
    }

    /**
     * Crée une exception pour un RIB dont la clé de contrôle ne correspond pas.
     *
     * @param rib          le RIB fourni
     * @param typeRib      "SOURCE" ou "DESTINATION"
     * @param cleFournie   la clé fournie dans le RIB (2 derniers chiffres)
     * @param cleCalculee  la clé calculée via modulo 97
     */
    public RibInvalideException(String rib, String typeRib, String cleFournie, String cleCalculee) {
        super("Le RIB " + typeRib.toLowerCase() + " " + rib + " est invalide. " +
              "Clé fournie : " + cleFournie + ", clé calculée : " + cleCalculee);
        this.rib = rib;
        this.typeRib = typeRib;
        this.details = new HashMap<>();
        this.details.put("rib", rib);
        this.details.put("type", typeRib);
        this.details.put("cleFournie", cleFournie);
        this.details.put("cleCalculee", cleCalculee);
    }

    /**
     * Crée une exception pour un format de RIB incorrect (longueur, caractères).
     *
     * @param rib     le RIB fourni
     * @param typeRib "SOURCE" ou "DESTINATION"
     */
    public static RibInvalideException formatInvalide(String rib, String typeRib) {
        String raison;
        if (rib == null || rib.isEmpty()) {
            raison = "Le RIB est vide";
        } else if (rib.length() != 20) {
            raison = "Le RIB doit contenir exactement 20 chiffres (longueur actuelle : " + rib.length() + ")";
        } else if (!rib.matches("^[0-9]{20}$")) {
            raison = "Le RIB doit contenir uniquement des chiffres";
        } else {
            raison = "Format de RIB invalide";
        }
        return new RibInvalideException(rib, typeRib, raison);
    }

    /**
     * Crée une exception pour un RIB source invalide.
     *
     * @param ribSource le RIB source invalide
     * @param raison    la raison de l'invalidité
     * @return l'exception
     */
    public static RibInvalideException sourceInvalide(String ribSource, String raison) {
        return new RibInvalideException(ribSource, "SOURCE", raison);
    }

    /**
     * Crée une exception pour un RIB destination invalide.
     *
     * @param ribDestination le RIB destination invalide
     * @param raison         la raison de l'invalidité
     * @return l'exception
     */
    public static RibInvalideException destinationInvalide(String ribDestination, String raison) {
        return new RibInvalideException(ribDestination, "DESTINATION", raison);
    }
}