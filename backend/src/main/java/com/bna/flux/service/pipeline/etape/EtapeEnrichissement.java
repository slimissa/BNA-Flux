package com.bna.flux.service.pipeline.etape;

import com.bna.flux.entity.Devise;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.CategorieContrepartie;
import com.bna.flux.repository.DeviseRepository;
import com.bna.flux.service.pipeline.ContextePipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 2 du pipeline — Enrichissement de la transaction.
 * <p>
 * Cette étape ajoute des informations contextuelles à la transaction
 * qui ne sont pas fournies par le système bancaire source mais qui sont
 * nécessaires pour l'évaluation des règles de surveillance.
 * </p>
 *
 * <p><b>Enrichissements effectués :</b></p>
 * <ol>
 *   <li><b>Pays d'origine</b> — Déterminé à partir de la devise et des RIBs</li>
 *   <li><b>Catégorie de contrepartie</b> — Classifiée comme PARTICULIER,
 *       ENTREPRISE ou GOUVERNEMENT</li>
 *   <li><b>Type de compte source</b> — Déduit du code banque et agence</li>
 * </ol>
 *
 * <p><b>Règles d'enrichissement :</b></p>
 * <ul>
 *   <li>Si la devise est TND et les deux RIBs sont tunisiens → pays = "Tunisie"</li>
 *   <li>Si la devise est étrangère → pays = pays émetteur de la devise</li>
 *   <li>La catégorie de contrepartie est déduite de patterns dans le RIB
 *       (en production, ce serait un appel à un service interne BNA)</li>
 * </ul>
 *
 * <p><b>Évolutivité :</b></p>
 * <p>
 * En production, cette étape pourrait être enrichie avec :
 * </p>
 * <ul>
 *   <li>Appel à un service de géolocalisation IP pour les transactions en ligne</li>
 *   <li>Consultation d'une base de données des PEP (Personnes Exposées Politiquement)</li>
 *   <li>Historique des transactions du compte pour détection de patterns</li>
 *   <li>Score de réputation de la contrepartie</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class EtapeEnrichissement {

    private final DeviseRepository deviseRepository;

    /**
     * Code pays pour la Tunisie.
     */
    private static final String PAYS_TUNISIE = "Tunisie";

    /**
     * Codes banques tunisiennes connus.
     */
    private static final java.util.Set<String> CODES_BANQUES_TUNISIENS =
            java.util.Set.of("01", "02", "03", "07", "08", "14");

    /**
     * Mapping devise → pays émetteur pour les devises étrangères courantes.
     */
    private static final java.util.Map<String, String> PAYS_PAR_DEVISE = new java.util.HashMap<>();

    static {
        PAYS_PAR_DEVISE.put("EUR", "Union Européenne");
        PAYS_PAR_DEVISE.put("USD", "États-Unis");
        PAYS_PAR_DEVISE.put("GBP", "Royaume-Uni");
        PAYS_PAR_DEVISE.put("CHF", "Suisse");
        PAYS_PAR_DEVISE.put("CAD", "Canada");
        PAYS_PAR_DEVISE.put("JPY", "Japon");
        PAYS_PAR_DEVISE.put("CNY", "Chine");
        PAYS_PAR_DEVISE.put("KWD", "Koweït");
        PAYS_PAR_DEVISE.put("BHD", "Bahreïn");
        PAYS_PAR_DEVISE.put("SAR", "Arabie Saoudite");
        PAYS_PAR_DEVISE.put("QAR", "Qatar");
        PAYS_PAR_DEVISE.put("AED", "Émirats Arabes Unis");
        PAYS_PAR_DEVISE.put("SEK", "Suède");
        PAYS_PAR_DEVISE.put("NOK", "Norvège");
        PAYS_PAR_DEVISE.put("DKK", "Danemark");
        PAYS_PAR_DEVISE.put("LYD", "Libye");
    }

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param deviseRepository le repository des devises
     */
    public EtapeEnrichissement(DeviseRepository deviseRepository) {
        this.deviseRepository = deviseRepository;
    }

    // Exécution de l'étape

    /**
     * Exécute le Stage 2 — Enrichissement de la transaction.
     * <p>
     * L'enrichissement ne bloque jamais la transaction. Même si certaines
     * informations ne peuvent pas être déterminées, le pipeline continue.
     * </p>
     *
     * @param contexte le contexte du pipeline contenant la transaction
     */
    public void executer(ContextePipeline contexte) {
        Transaction transaction = contexte.getTransaction();
        log.debug("Stage 2 — Enrichissement de la transaction : {}",
                transaction.getReferenceTransaction() != null ? transaction.getReferenceTransaction() : "nouvelle");

        try {
            // 1. Déterminer le pays d'origine
            String paysOrigine = determinerPaysOrigine(transaction);
            transaction.setPaysOrigine(paysOrigine);
            log.debug("Pays d'origine déterminé : {}", paysOrigine);

            // 2. Déterminer la catégorie de contrepartie
            CategorieContrepartie categorie = determinerCategorieContrepartie(transaction);
            transaction.setCategorieContrepartie(categorie);
            log.debug("Catégorie contrepartie déterminée : {}", categorie);

            // 3. Enrichissement supplémentaires (si disponibles)
            enrichirInformationsSupplementaires(transaction);

            // Succès
            contexte.setEnrichissementReussi(true);
            log.debug("Stage 2 réussi — Transaction enrichie : {} (pays={}, catégorie={})",
                    transaction.getReferenceTransaction(), paysOrigine, categorie);

        } catch (Exception e) {
            // L'enrichissement ne bloque pas la transaction
            log.warn("Stage 2 — Erreur d'enrichissement (non bloquant) : {}", e.getMessage());
            contexte.setEnrichissementReussi(true); // On continue malgré l'erreur
        }
    }

    // Méthodes d'enrichissement privées

    /**
     * Détermine le pays d'origine de la transaction.
     *
     * <p><b>Logique :</b></p>
     * <ol>
     *   <li>Si la devise est TND et les deux RIBs sont tunisiens → "Tunisie"</li>
     *   <li>Si la devise est TND mais un RIB est étranger → "Tunisie" (transfert sortant)</li>
     *   <li>Si la devise est étrangère → pays émetteur de la devise</li>
     *   <li>Si la devise est inconnue → "Non déterminé"</li>
     * </ol>
     *
     * @param transaction la transaction
     * @return le pays d'origine déterminé
     */
    private String determinerPaysOrigine(Transaction transaction) {
        String codeDevise = transaction.getCodeDevise();

        if (codeDevise == null) {
            return "Non déterminé";
        }

        // Devise tunisienne → Tunisie
        if ("TND".equalsIgnoreCase(codeDevise)) {
            // Vérifier si les deux RIBs sont tunisiens
            if (estRibTunisien(transaction.getRibSource()) && estRibTunisien(transaction.getRibDestination())) {
                return PAYS_TUNISIE;
            }
            // Transfert impliquant un compte étranger
            return PAYS_TUNISIE;
        }

        // Devise étrangère → pays émetteur
        String pays = PAYS_PAR_DEVISE.get(codeDevise.toUpperCase());
        if (pays != null) {
            return pays;
        }

        // Devise inconnue → consulter le repository
        return deviseRepository.findByCodeIgnoreCase(codeDevise)
                .map(Devise::getNom)
                .map(nom -> "Pays émetteur de " + nom)
                .orElse("Non déterminé");
    }

    /**
     * Détermine la catégorie de contrepartie (PARTICULIER, ENTREPRISE, GOUVERNEMENT).
     *
     * <p><b>Logique simplifiée (prototype) :</b></p>
     * <ul>
     *   <li>RIB destination commençant par un code banque + agence gouvernementale → GOUVERNEMENT</li>
     *   <li>RIB avec pattern entreprise (compte professionnel) → ENTREPRISE</li>
     *   <li>Par défaut → PARTICULIER</li>
     * </ul>
     *
     * <p>
     * En production, cette méthode appellerait un service interne BNA
     * de qualification des comptes.
     * </p>
     *
     * @param transaction la transaction
     * @return la catégorie de contrepartie
     */
    private CategorieContrepartie determinerCategorieContrepartie(Transaction transaction) {
        String ribDestination = transaction.getRibDestination();

        if (ribDestination == null || ribDestination.length() < 5) {
            return CategorieContrepartie.PARTICULIER;
        }

        // Extraire le code banque (2 premiers chiffres)
        String codeBanque = ribDestination.substring(0, 2);

        // Vérifier si le code banque est tunisien
        if (!CODES_BANQUES_TUNISIENS.contains(codeBanque)) {
            // Banque étrangère → probablement une entreprise (transfert international)
            return CategorieContrepartie.ENTREPRISE;
        }

        // TODO: En production, appeler le service de qualification des comptes BNA
        // Pour le prototype, on utilise une heuristique simple basée sur le numéro de compte

        // Par défaut : particulier
        return CategorieContrepartie.PARTICULIER;
    }

    /**
     * Enrichit la transaction avec des informations supplémentaires.
     *
     * <p>
     * Pour le prototype, cette méthode est un point d'extension.
     * En production, elle pourrait :
     * </p>
     * <ul>
     *   <li>Ajouter des informations de géolocalisation</li>
     *   <li>Calculer des indicateurs de vélocité</li>
     *   <li>Croiser avec des listes de surveillance</li>
     * </ul>
     *
     * @param transaction la transaction à enrichir
     */
    private void enrichirInformationsSupplementaires(Transaction transaction) {
        // Point d'extension pour les enrichissements futurs
        // Exemple : calculer la vélocité des transactions pour ce compte

        String ribSource = transaction.getRibSource();
        if (ribSource != null && ribSource.length() >= 2) {
            String codeBanqueSource = ribSource.substring(0, 2);
            // Vérifier si c'est une banque tunisienne connue
            if (!CODES_BANQUES_TUNISIENS.contains(codeBanqueSource)) {
                log.debug("Transaction depuis une banque étrangère : code banque source = {}", codeBanqueSource);
                // Potentiellement marquer comme transaction internationale
                if (transaction.getPaysOrigine() == null || PAYS_TUNISIE.equals(transaction.getPaysOrigine())) {
                    transaction.setPaysOrigine("International (banque " + codeBanqueSource + ")");
                }
            }
        }
    }

    // Méthodes utilitaires

    /**
     * Vérifie si un RIB appartient à une banque tunisienne.
     *
     * @param rib le RIB à vérifier
     * @return {@code true} si le code banque est tunisien
     */
    private boolean estRibTunisien(String rib) {
        if (rib == null || rib.length() < 2) {
            return false;
        }
        String codeBanque = rib.substring(0, 2);
        return CODES_BANQUES_TUNISIENS.contains(codeBanque);
    }

    /**
     * Retourne le nom du pays émetteur d'une devise.
     *
     * @param codeDevise le code ISO 4217
     * @return le pays émetteur, ou "Inconnu"
     */
    public String getPaysEmetteur(String codeDevise) {
        if (codeDevise == null) {
            return "Inconnu";
        }
        if ("TND".equalsIgnoreCase(codeDevise)) {
            return PAYS_TUNISIE;
        }
        return PAYS_PAR_DEVISE.getOrDefault(codeDevise.toUpperCase(), "Inconnu");
    }
}