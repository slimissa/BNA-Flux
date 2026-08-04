package com.bna.flux.service.pipeline.etape;

import com.bna.flux.entity.Transaction;
import com.bna.flux.exception.DeviseInconnueException;
import com.bna.flux.exception.DisjoncteurOuvertException;
import com.bna.flux.exception.RibInvalideException;
import com.bna.flux.repository.DeviseRepository;
import com.bna.flux.service.ServiceDisjoncteur;
import com.bna.flux.service.ValidateurRib;
import com.bna.flux.service.pipeline.ContextePipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Stage 1 du pipeline — Validation de la transaction.
 * <p>
 * Cette étape vérifie l'intégrité et la validité des données de la transaction
 * avant tout traitement ultérieur. Si une validation échoue, le pipeline est
 * immédiatement interrompu et la transaction est marquée comme BLOQUEE.
 * </p>
 *
 * <p><b>Validations effectuées :</b></p>
 * <ol>
 *   <li><b>RIB source</b> — Format 20 chiffres + clé modulo 97</li>
 *   <li><b>RIB destination</b> — Format 20 chiffres + clé modulo 97</li>
 *   <li><b>Devise</b> — Code ISO 4217 existant et actif</li>
 *   <li><b>Montant</b> — Positif et cohérent avec les unités mineures de la devise</li>
 *   <li><b>Disjoncteur source</b> — Vérification circuit breaker RIB source</li>
 *   <li><b>Disjoncteur destination</b> — Vérification circuit breaker RIB destination</li>
 *   <li><b>Disjoncteur agence</b> — Vérification circuit breaker code agence</li>
 *   <li><b>Disjoncteur canal</b> — Vérification circuit breaker canal</li>
 * </ol>
 *
 * <p><b>Comportement en cas d'erreur :</b></p>
 * <ul>
 *   <li>Toute erreur de validation interrompt immédiatement le pipeline</li>
 *   <li>Le contexte est marqué comme interrompu avec la raison</li>
 *   <li>Les étapes suivantes ne sont pas exécutées</li>
 *   <li>Les erreurs sont logguées avec le niveau WARN</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class EtapeValidation {

    private final ValidateurRib validateurRib;
    private final DeviseRepository deviseRepository;
    private final ServiceDisjoncteur serviceDisjoncteur;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param validateurRib     le service de validation des RIB
     * @param deviseRepository  le repository des devises
     * @param serviceDisjoncteur le service de gestion des disjoncteurs
     */
    public EtapeValidation(ValidateurRib validateurRib,
                           DeviseRepository deviseRepository,
                           ServiceDisjoncteur serviceDisjoncteur) {
        this.validateurRib = validateurRib;
        this.deviseRepository = deviseRepository;
        this.serviceDisjoncteur = serviceDisjoncteur;
    }

    // Exécution de l'étape

    /**
     * Exécute le Stage 1 — Validation de la transaction.
     * <p>
     * Si toutes les validations passent, le contexte est marqué comme
     * {@code validationReussie = true} et le pipeline continue.
     * Si une validation échoue, le contexte est marqué comme interrompu.
     * </p>
     *
     * @param contexte le contexte du pipeline contenant la transaction
     */
    public void executer(ContextePipeline contexte) {
        Transaction transaction = contexte.getTransaction();
        log.debug("Stage 1 — Validation de la transaction : {}", 
                transaction.getReferenceTransaction() != null ? transaction.getReferenceTransaction() : "nouvelle");

        try {
            // 1. Valider le RIB source
            validerRibSource(transaction);

            // 2. Valider le RIB destination
            validerRibDestination(transaction);

            // 3. Valider la devise
            validerDevise(transaction);

            // 4. Valider le montant
            validerMontant(transaction);

            // 5. Vérifier les disjoncteurs
            verifierDisjoncteurs(transaction);

            // Succès — toutes les validations ont passé
            contexte.setValidationReussie(true);
            log.debug("Stage 1 réussi — Transaction validée : {}", transaction.getReferenceTransaction());

        } catch (RibInvalideException e) {
            log.warn("Stage 1 échoué — RIB invalide : {}", e.getMessage());
            contexte.interrompre("VALIDATION", e.getMessage());
            transaction.setStatut(Transaction.StatutTransaction.BLOQUE);
            transaction.setMotif("RIB invalide : " + e.getMessage());

        } catch (DeviseInconnueException e) {
            log.warn("Stage 1 échoué — Devise invalide : {}", e.getMessage());
            contexte.interrompre("VALIDATION", e.getMessage());
            transaction.setStatut(Transaction.StatutTransaction.BLOQUE);
            transaction.setMotif("Devise invalide : " + e.getMessage());

        } catch (DisjoncteurOuvertException e) {
            log.warn("Stage 1 échoué — Disjoncteur ouvert : {}", e.getMessage());
            contexte.interrompre("VALIDATION", e.getMessage());
            transaction.setStatut(Transaction.StatutTransaction.BLOQUE);
            transaction.setMotif("Disjoncteur ouvert : " + e.getMessage());

        } catch (IllegalArgumentException e) {
            log.warn("Stage 1 échoué — Erreur de validation : {}", e.getMessage());
            contexte.interrompre("VALIDATION", e.getMessage());
            transaction.setStatut(Transaction.StatutTransaction.BLOQUE);
            transaction.setMotif("Erreur de validation : " + e.getMessage());

        } catch (Exception e) {
            log.error("Stage 1 échoué — Erreur inattendue : {}", e.getMessage(), e);
            contexte.interrompre("VALIDATION", "Erreur interne lors de la validation");
            transaction.setStatut(Transaction.StatutTransaction.BLOQUE);
            transaction.setMotif("Erreur interne lors de la validation");
        }
    }

    // Méthodes de validation privées

    /**
     * Valide le RIB source de la transaction.
     *
     * @param transaction la transaction
     * @throws RibInvalideException si le RIB source est invalide
     */
    private void validerRibSource(Transaction transaction) throws RibInvalideException {
        if (transaction.getRibSource() == null || transaction.getRibSource().isEmpty()) {
            throw new RibInvalideException("", "SOURCE", "Le RIB source est vide");
        }

        // Normaliser le RIB (supprimer espaces)
        String ribNormalise = validateurRib.normaliser(transaction.getRibSource());

        // Valider le format et la clé
        validateurRib.valider(ribNormalise, "SOURCE");

        // Mettre à jour avec la version normalisée
        transaction.setRibSource(ribNormalise);

        log.debug("RIB source valide : {}", ribNormalise);
    }

    /**
     * Valide le RIB destination de la transaction.
     *
     * @param transaction la transaction
     * @throws RibInvalideException si le RIB destination est invalide
     */
    private void validerRibDestination(Transaction transaction) throws RibInvalideException {
        if (transaction.getRibDestination() == null || transaction.getRibDestination().isEmpty()) {
            throw new RibInvalideException("", "DESTINATION", "Le RIB destination est vide");
        }

        // Normaliser le RIB
        String ribNormalise = validateurRib.normaliser(transaction.getRibDestination());

        // Valider le format et la clé
        validateurRib.valider(ribNormalise, "DESTINATION");

        // Vérifier que les RIBs source et destination sont différents
        if (ribNormalise.equals(transaction.getRibSource())) {
            throw new RibInvalideException(ribNormalise, "DESTINATION",
                    "Le RIB destination doit être différent du RIB source");
        }

        // Mettre à jour avec la version normalisée
        transaction.setRibDestination(ribNormalise);

        log.debug("RIB destination valide : {}", ribNormalise);
    }

    /**
     * Valide la devise de la transaction.
     *
     * @param transaction la transaction
     * @throws DeviseInconnueException si la devise est invalide ou inactive
     */
    private void validerDevise(Transaction transaction) throws DeviseInconnueException {
        if (transaction.getCodeDevise() == null || transaction.getCodeDevise().isEmpty()) {
            throw new DeviseInconnueException("");
        }

        String codeDevise = transaction.getCodeDevise().toUpperCase();

        // Vérifier que la devise existe
        var deviseOpt = deviseRepository.findByCodeIgnoreCase(codeDevise);
        if (deviseOpt.isEmpty()) {
            throw DeviseInconnueException.introuvable(codeDevise);
        }

        // Vérifier que la devise est active
        if (!deviseOpt.get().isActif()) {
            throw DeviseInconnueException.inactive(codeDevise);
        }

        // Associer l'entité Devise à la transaction
        transaction.setDevise(deviseOpt.get());
        // La devise est déjà associée via transaction.setDevise() — pas de setCodeDevise séparé

        log.debug("Devise valide : {}", codeDevise);
    }

    /**
     * Valide le montant de la transaction.
     *
     * @param transaction la transaction
     * @throws IllegalArgumentException si le montant est invalide
     */
    private void validerMontant(Transaction transaction) {
        if (transaction.getMontant() == null) {
            throw new IllegalArgumentException("Le montant est obligatoire");
        }

        if (transaction.getMontant().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro");
        }

        // Vérifier le nombre de décimales par rapport aux unités mineures de la devise
        if (transaction.getDevise() != null) {
            int unitesMineures = transaction.getDevise().getUnitesMineures();
            int decimales = transaction.getMontant().scale();

            if (decimales > unitesMineures) {
                throw new IllegalArgumentException(
                        "Le montant ne peut pas avoir plus de " + unitesMineures +
                        " décimales pour la devise " + transaction.getCodeDevise() +
                        " (actuel : " + decimales + ")"
                );
            }
        }

        log.debug("Montant valide : {} {}", transaction.getMontant(), transaction.getCodeDevise());
    }

    /**
     * Vérifie les disjoncteurs pour tous les types de cibles pertinents.
     *
     * @param transaction la transaction
     * @throws DisjoncteurOuvertException si un disjoncteur est ouvert
     */
    private void verifierDisjoncteurs(Transaction transaction) throws DisjoncteurOuvertException {
        // Vérifier le disjoncteur pour le RIB source
        serviceDisjoncteur.verifierAvantTransaction(
                com.bna.flux.entity.EtatDisjoncteur.TypeCible.COMPTE_SOURCE,
                transaction.getRibSource()
        );

        // Vérifier le disjoncteur pour le RIB destination
        serviceDisjoncteur.verifierAvantTransaction(
                com.bna.flux.entity.EtatDisjoncteur.TypeCible.COMPTE_DESTINATION,
                transaction.getRibDestination()
        );

        // Vérifier le disjoncteur pour l'agence (extraite du RIB source)
        String codeAgence = transaction.getCodeAgenceSource();
        if (codeAgence != null && !codeAgence.isEmpty()) {
            serviceDisjoncteur.verifierAvantTransaction(
                    com.bna.flux.entity.EtatDisjoncteur.TypeCible.AGENCE,
                    codeAgence
            );
        }

        // Vérifier le disjoncteur pour le canal
        if (transaction.getCanal() != null) {
            serviceDisjoncteur.verifierAvantTransaction(
                    com.bna.flux.entity.EtatDisjoncteur.TypeCible.CANAL,
                    transaction.getCanal().name()
            );
        }

        log.debug("Vérification des disjoncteurs réussie");
    }
}