package com.bna.flux.service;

import com.bna.flux.exception.RibInvalideException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * Service de validation des RIB (Relevé d'Identité Bancaire) tunisiens.
 * <p>
 * Implémente l'algorithme de vérification de la clé de contrôle modulo 97
 * utilisé par le système bancaire tunisien. La validation est effectuée
 * lors du Stage 1 (Validation) du pipeline pour les RIBs source et destination
 * de chaque transaction.
 * </p>
 *
 * <p><b>Structure du RIB tunisien (20 chiffres) :</b></p>
 * <pre>
 * Position : 1-2    3-5     6-18        19-20
 * Contenu  : BB     CCC     AAAAAAAAAAA KK
 *            Banque Agence  Compte       Clé
 * </pre>
 *
 * <p><b>Algorithme de validation :</b></p>
 * <ol>
 *   <li>Extraire les 18 premiers chiffres (banque + agence + compte)</li>
 *   <li>Former le nombre N à partir de ces 18 chiffres</li>
 *   <li>Calculer : {@code clé attendue = 97 - ((N × 100) mod 97)}</li>
 *   <li>Si le résultat = 97, la clé attendue = 00</li>
 *   <li>Comparer la clé attendue avec la clé fournie (positions 19-20)</li>
 * </ol>
 *
 * <p><b>Exemple de validation :</b></p>
 * <pre>
 * RIB : 08601000191000748054
 * N = 086010001910007480 (18 premiers chiffres)
 * N × 100 = 8601000191000748000
 * (N × 100) mod 97 = 43
 * Clé attendue = 97 - 43 = 54
 * Clé fournie = 54 → RIB VALIDE ✅
 * </pre>
 *
 * <p><b>Note technique :</b> L'utilisation de {@link BigInteger} est obligatoire
 * car les nombres manipulés (18-20 chiffres) dépassent la capacité d'un {@code long}
 * (maximum 9 223 372 036 854 775 807 soit ~19 chiffres, mais insuffisant pour
 * le calcul intermédiaire N × 100 qui peut atteindre 20 chiffres).</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class ValidateurRib {

    /**
     * Longueur totale d'un RIB tunisien.
     */
    public static final int LONGUEUR_RIB = 20;

    /**
     * Longueur du code banque.
     */
    public static final int LONGUEUR_CODE_BANQUE = 2;

    /**
     * Longueur du code agence.
     */
    public static final int LONGUEUR_CODE_AGENCE = 3;

    /**
     * Longueur du numéro de compte.
     */
    public static final int LONGUEUR_COMPTE = 13;

    /**
     * Longueur de la clé de contrôle.
     */
    public static final int LONGUEUR_CLE = 2;

    /**
     * Modulo utilisé pour le calcul de la clé.
     */
    public static final int MODULO = 97;

    /**
     * Nombre de chiffres avant la clé (banque + agence + compte).
     */
    public static final int LONGUEUR_SANS_CLE = LONGUEUR_RIB - LONGUEUR_CLE;

    /**
     * Multiplicateur appliqué avant le modulo (décalage de 2 chiffres pour la clé).
     */
    public static final int MULTIPLICATEUR = 100;

    /**
     * Validation du RIB activée ou non (configurable).
     */
    @Value("${bna.rib.validation-active:true}")
    private boolean validationActive;

    // Validation principale

    /**
     * Valide un RIB tunisien complet.
     * <p>
     * Vérifie :
     * </p>
     * <ol>
     *   <li>Que le RIB n'est pas null ou vide</li>
     *   <li>Qu'il contient exactement 20 chiffres</li>
     *   <li>Qu'il ne contient que des chiffres</li>
     *   <li>Que la clé de contrôle correspond au calcul modulo 97</li>
     * </ol>
     *
     * @param rib le RIB à valider (20 chiffres)
     * @throws RibInvalideException si le RIB est invalide
     */
    public void valider(String rib) throws RibInvalideException {
        valider(rib, "SOURCE");
    }

    /**
     * Valide un RIB tunisien avec indication du type (source ou destination).
     *
     * @param rib     le RIB à valider (20 chiffres)
     * @param typeRib "SOURCE" ou "DESTINATION"
     * @throws RibInvalideException si le RIB est invalide
     */
    public void valider(String rib, String typeRib) throws RibInvalideException {
        // Si la validation est désactivée (tests), on accepte tout
        if (!validationActive) {
            log.warn("Validation RIB désactivée — RIB {} accepté sans vérification", rib);
            return;
        }

        // Étape 1 : Vérifier que le RIB n'est pas null ou vide
        if (rib == null || rib.isEmpty()) {
            throw RibInvalideException.formatInvalide(rib, typeRib);
        }

        // Étape 2 : Vérifier la longueur
        if (rib.length() != LONGUEUR_RIB) {
            throw RibInvalideException.formatInvalide(rib, typeRib);
        }

        // Étape 3 : Vérifier que tous les caractères sont des chiffres
        if (!rib.matches("^[0-9]{" + LONGUEUR_RIB + "}$")) {
            throw RibInvalideException.formatInvalide(rib, typeRib);
        }

        // Étape 4 : Extraire les composants
        String codeBanque = extraireCodeBanque(rib);
        String codeAgence = extraireCodeAgence(rib);
        String numeroCompte = extraireNumeroCompte(rib);
        String cleFournie = extraireCle(rib);

        // Étape 5 : Calculer la clé attendue
        String cleCalculee = calculerCle(codeBanque, codeAgence, numeroCompte);

        // Étape 6 : Comparer
        if (!cleFournie.equals(cleCalculee)) {
            log.debug("RIB {} invalide : clé fournie = {}, clé calculée = {}", rib, cleFournie, cleCalculee);
            throw new RibInvalideException(rib, typeRib, cleFournie, cleCalculee);
        }

        log.debug("RIB {} valide (clé = {})", rib, cleFournie);
    }

    /**
     * Valide un RIB et retourne un booléen (sans exception).
     *
     * @param rib le RIB à valider
     * @return {@code true} si le RIB est valide
     */
    public boolean estValide(String rib) {
        try {
            valider(rib);
            return true;
        } catch (RibInvalideException e) {
            return false;
        }
    }

    // Extraction des composants du RIB

    /**
     * Extrait le code banque (2 premiers chiffres).
     *
     * @param rib le RIB complet (20 chiffres)
     * @return le code banque (2 chiffres)
     * @throws RibInvalideException si le RIB est trop court
     */
    public String extraireCodeBanque(String rib) {
        if (rib == null || rib.length() < LONGUEUR_CODE_BANQUE) {
            throw new RibInvalideException(rib, "EXTRACTION", "RIB trop court pour extraire le code banque");
        }
        return rib.substring(0, 2);
    }

    /**
     * Extrait le code agence (positions 3-5).
     *
     * @param rib le RIB complet (20 chiffres)
     * @return le code agence (3 chiffres)
     * @throws RibInvalideException si le RIB est trop court
     */
    public String extraireCodeAgence(String rib) {
        if (rib == null || rib.length() < LONGUEUR_CODE_BANQUE + LONGUEUR_CODE_AGENCE) {
            throw new RibInvalideException(rib, "EXTRACTION", "RIB trop court pour extraire le code agence");
        }
        return rib.substring(2, 5);
    }

    /**
     * Extrait le numéro de compte (positions 6-18).
     *
     * @param rib le RIB complet (20 chiffres)
     * @return le numéro de compte (13 chiffres)
     * @throws RibInvalideException si le RIB est trop court
     */
    public String extraireNumeroCompte(String rib) {
        if (rib == null || rib.length() < LONGUEUR_RIB) {
            throw new RibInvalideException(rib, "EXTRACTION", "RIB trop court pour extraire le numéro de compte");
        }
        return rib.substring(5, 18);
    }

    /**
     * Extrait la clé de contrôle (positions 19-20).
     *
     * @param rib le RIB complet (20 chiffres)
     * @return la clé (2 chiffres)
     * @throws RibInvalideException si le RIB est trop court
     */
    public String extraireCle(String rib) {
        if (rib == null || rib.length() < LONGUEUR_RIB) {
            throw new RibInvalideException(rib, "EXTRACTION", "RIB trop court pour extraire la clé");
        }
        return rib.substring(18, 20);
    }

    /**
     * Extrait les 18 premiers chiffres du RIB (sans la clé).
     *
     * @param rib le RIB complet (20 chiffres)
     * @return les 18 premiers chiffres
     */
    public String extraireSansCle(String rib) {
        if (rib == null || rib.length() < LONGUEUR_RIB) {
            throw new RibInvalideException(rib, "EXTRACTION", "RIB trop court");
        }
        return rib.substring(0, 18);
    }

    // Calcul de la clé de contrôle

    /**
     * Calcule la clé de contrôle d'un RIB tunisien selon l'algorithme modulo 97.
     *
     * <p><b>Algorithme :</b></p>
     * <ol>
     *   <li>Concaténer les composants : N = banque + agence + compte (18 chiffres)</li>
     *   <li>Multiplier par 100 : N × 100</li>
     *   <li>Calculer le reste modulo 97 : R = (N × 100) mod 97</li>
     *   <li>Clé = 97 - R</li>
     *   <li>Si Clé = 97, Clé = 00</li>
     * </ol>
     *
     * @param codeBanque   le code banque (2 chiffres)
     * @param codeAgence   le code agence (3 chiffres)
     * @param numeroCompte le numéro de compte (13 chiffres)
     * @return la clé calculée (2 chiffres, avec leading zero si nécessaire)
     */
    public String calculerCle(String codeBanque, String codeAgence, String numeroCompte) {
        // Concaténer les 18 chiffres
        String nombreSansCle = codeBanque + codeAgence + numeroCompte;

        // Valider que nous avons bien 18 chiffres
        if (nombreSansCle.length() != LONGUEUR_SANS_CLE) {
            throw new IllegalArgumentException(
                    "Les composants du RIB doivent totaliser " + LONGUEUR_SANS_CLE +
                    " chiffres (actuel : " + nombreSansCle.length() + ")"
            );
        }

        return calculerCle(nombreSansCle);
    }

    /**
     * Calcule la clé de contrôle à partir des 18 premiers chiffres du RIB.
     *
     * @param dixHuitPremiersChiffres les 18 premiers chiffres du RIB
     * @return la clé calculée (2 chiffres)
     */
    private String calculerCle(String dixHuitPremiersChiffres) {
        // Utiliser BigInteger pour éviter l'overflow (20 chiffres après ×100)
        BigInteger n = new BigInteger(dixHuitPremiersChiffres);

        // N × 100 (décalage pour laisser la place à la clé)
        BigInteger nMult = n.multiply(BigInteger.valueOf(MULTIPLICATEUR));

        // (N × 100) mod 97
        BigInteger reste = nMult.mod(BigInteger.valueOf(MODULO));

        // Clé = 97 - reste
        int cleInt = MODULO - reste.intValue();

        // Si clé = 97, clé = 00
        if (cleInt == MODULO) {
            cleInt = 0;
        }

        // Formater sur 2 chiffres avec leading zero
        return String.format("%02d", cleInt);
    }

    // Méthodes utilitaires

    /**
     * Normalise un RIB en supprimant les espaces et caractères non numériques.
     *
     * @param rib le RIB potentiellement formaté
     * @return le RIB nettoyé (uniquement des chiffres)
     */
    public String normaliser(String rib) {
        if (rib == null) {
            return null;
        }
        return rib.replaceAll("[^0-9]", "");
    }

    /**
     * Formate un RIB pour l'affichage avec des espaces tous les 4 caractères.
     *
     * @param rib le RIB brut (20 chiffres)
     * @return le RIB formaté (ex: "0860 1000 1910 0074 8054")
     */
    public String formater(String rib) {
        if (rib == null || rib.length() != LONGUEUR_RIB) {
            return rib;
        }
        return rib.replaceAll("(.{4})", "$1 ").trim();
    }

    /**
     * Génère un RIB valide aléatoire pour les tests.
     * <p>
     * <b>ATTENTION :</b> Cette méthode est destinée UNIQUEMENT aux tests.
     * Ne pas utiliser en production.
     * </p>
     *
     * @param codeBanque le code banque (2 chiffres)
     * @param codeAgence le code agence (3 chiffres)
     * @return un RIB valide de 20 chiffres
     */
    public String genererRibTest(String codeBanque, String codeAgence) {
        // Générer un numéro de compte aléatoire de 13 chiffres
        StringBuilder compte = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < LONGUEUR_COMPTE; i++) {
            compte.append(random.nextInt(10));
        }

        // Calculer la clé
        String cle = calculerCle(codeBanque, codeAgence, compte.toString());

        // Assembler le RIB
        return codeBanque + codeAgence + compte + cle;
    }

    /**
     * Vérifie si un code banque est connu.
     * <p>
     * Codes banques tunisiens courants :
     * </p>
     * <ul>
     *   <li>01 — BNA (Banque Nationale Agricole)</li>
     *   <li>02 — BIAT</li>
     *   <li>03 — STB</li>
     *   <li>07 — Amen Bank</li>
     *   <li>08 — ATB</li>
     *   <li>14 — BT</li>
     * </ul>
     *
     * @param codeBanque le code banque (2 chiffres)
     * @return {@code true} si le code banque est reconnu
     */
    public boolean estCodeBanqueConnu(String codeBanque) {
        return codeBanque != null && codeBanque.matches("^(01|02|03|07|08|14)$");
    }
}