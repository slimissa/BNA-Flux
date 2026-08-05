package com.bna.flux.service;

import com.bna.flux.dto.ReponseTransaction;
import com.bna.flux.dto.RequeteTransaction;
import com.bna.flux.entity.Devise;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.exception.DeviseInconnueException;
import com.bna.flux.repository.DeviseRepository;
import com.bna.flux.repository.TransactionRepository;
import com.bna.flux.service.pipeline.ContextePipeline;
import com.bna.flux.service.pipeline.MoteurPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service de gestion des transactions.
 * <p>
 * Point d'entrée unique pour toutes les opérations liées aux transactions :
 * soumission au pipeline, consultation avec filtrage, recherche par référence,
 * et consultation de la piste d'audit.
 * </p>
 *
 * <p><b>Responsabilités :</b></p>
 * <ul>
 *   <li>Transformer un {@link RequeteTransaction} en entité {@link Transaction}</li>
 *   <li>Soumettre la transaction au {@link MoteurPipeline}</li>
 *   <li>Construire la {@link ReponseTransaction} à partir du résultat du pipeline</li>
 *   <li>Fournir les méthodes de consultation avec filtrage dynamique</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class ServiceTransaction {

    private final TransactionRepository transactionRepository;
    private final DeviseRepository deviseRepository;
    private final MoteurPipeline moteurPipeline;
    private final ServiceAudit serviceAudit;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param transactionRepository le repository des transactions
     * @param deviseRepository      le repository des devises
     * @param moteurPipeline        le moteur du pipeline
     * @param serviceAudit          le service d'audit
     */
    public ServiceTransaction(TransactionRepository transactionRepository,
                               DeviseRepository deviseRepository,
                               MoteurPipeline moteurPipeline,
                               ServiceAudit serviceAudit) {
        this.transactionRepository = transactionRepository;
        this.deviseRepository = deviseRepository;
        this.moteurPipeline = moteurPipeline;
        this.serviceAudit = serviceAudit;
    }

    // Soumission d'une transaction au pipeline

    /**
     * Soumet une nouvelle transaction au pipeline de surveillance.
     * <p>
     * Cette méthode :
     * </p>
     * <ol>
     *   <li>Transforme le DTO en entité Transaction</li>
     *   <li>Associe la devise</li>
     *   <li>Soumet la transaction au pipeline</li>
     *   <li>Construit la réponse à partir du résultat</li>
     * </ol>
     *
     * @param requete le DTO contenant les données de la transaction
     * @return la réponse avec le résultat du pipeline
     */
    @Transactional
    public ReponseTransaction soumettre(RequeteTransaction requete) {
        log.info("Soumission d'une nouvelle transaction — Montant: {} {}, Type: {}, Canal: {}",
                requete.getMontant(), requete.getCodeDevise(),
                requete.getTypeTransaction(), requete.getCanal());

        // 1. Créer l'entité Transaction à partir du DTO
        Transaction transaction = creerEntite(requete);

        // 2. Exécuter le pipeline
        ContextePipeline contexte = moteurPipeline.executer(transaction);

        // 3. Construire la réponse
        return construireReponse(contexte);
    }

    /**
     * Crée l'entité {@link Transaction} à partir du DTO de requête.
     *
     * @param requete le DTO de requête
     * @return l'entité Transaction créée (non persistée)
     */
    private Transaction creerEntite(RequeteTransaction requete) {
        // Charger la devise
        Devise devise = deviseRepository.findByCodeIgnoreCase(requete.getCodeDevise())
                .orElseThrow(() -> DeviseInconnueException.introuvable(requete.getCodeDevise()));

        if (!devise.isActif()) {
            throw DeviseInconnueException.inactive(requete.getCodeDevise());
        }

        // Créer la transaction
        Transaction transaction = Transaction.builder()
                .ribSource(requete.getRibSource())
                .ribDestination(requete.getRibDestination())
                .montant(requete.getMontant())
                .devise(devise)
                .typeTransaction(requete.getTypeTransaction())
                .canal(requete.getCanal())
                .dateTransaction(requete.getDateTransaction() != null
                        ? requete.getDateTransaction()
                        : LocalDateTime.now())
                .description(requete.getDescription())
                .statut(StatutTransaction.ACCEPTE) // Statut initial — sera mis à jour par le pipeline
                .scoreRisque(BigDecimal.ZERO)
                .build();

        log.debug("Entité Transaction créée — Réf: {}", transaction.getReferenceTransaction());
        return transaction;
    }

    /**
     * Construit la réponse DTO à partir du contexte du pipeline.
     *
     * @param contexte le contexte après exécution du pipeline
     * @return la réponse transaction complète
     */
    private ReponseTransaction construireReponse(ContextePipeline contexte) {
        Transaction transaction = contexte.getTransaction();

        // Mapper les alertes
        List<com.bna.flux.dto.ReponseAlerte> alertes = contexte.getAlertesGenerees().stream()
                .map(this::mapperAlerte)
                .collect(Collectors.toList());

        // Construire la réponse
        ReponseTransaction reponse = ReponseTransaction.builder()
                .statut("SUCCES")
                .id(transaction.getId())
                .referenceTransaction(transaction.getReferenceTransaction())
                .ribSource(transaction.getRibSource())
                .ribDestination(transaction.getRibDestination())
                .montant(transaction.getMontant())
                .codeDevise(transaction.getCodeDevise())
                .nomDevise(transaction.getDevise() != null ? transaction.getDevise().getNom() : null)
                .symboleDevise(transaction.getDevise() != null ? transaction.getDevise().getSymbole() : null)
                .typeTransaction(transaction.getTypeTransaction())
                .canal(transaction.getCanal())
                .dateTransaction(transaction.getDateTransaction())
                .description(transaction.getDescription())
                .paysOrigine(transaction.getPaysOrigine())
                .categorieContrepartie(transaction.getCategorieContrepartie() != null
                        ? transaction.getCategorieContrepartie().name() : null)
                .scoreRisque(transaction.getScoreRisque())
                .statutTransaction(transaction.getStatut())
                .motif(transaction.getMotif())
                .traiteLe(transaction.getTraiteLe())
                .dateCreation(transaction.getDateCreation())
                .alertes(alertes)
                .nombreAlertes(alertes.size())
                .pisteAuditDisponible(transaction.getId() != null)
                .build();

        // Si le pipeline a été interrompu, ajouter l'information
        if (contexte.isInterrompu()) {
            reponse.setStatut("ATTENTION");
            reponse.setMotif((reponse.getMotif() != null ? reponse.getMotif() + " | " : "")
                    + "Pipeline interrompu à l'étape " + contexte.getEtapeArret()
                    + " : " + contexte.getRaisonArret());
        }

        log.info("Réponse transaction construite — {} ({}), Score: {}, Alertes: {}",
                reponse.getReferenceTransaction(),
                reponse.getStatutTransaction(),
                reponse.getScoreRisque(),
                reponse.getNombreAlertes());

        return reponse;
    }

    /**
     * Mappe une entité {@link com.bna.flux.entity.Alerte} vers un DTO {@link com.bna.flux.dto.ReponseAlerte}.
     *
     * @param alerte l'entité alerte
     * @return le DTO alerte
     */
    private com.bna.flux.dto.ReponseAlerte mapperAlerte(com.bna.flux.entity.Alerte alerte) {
        if (alerte == null) {
            return null;
        }

        return com.bna.flux.dto.ReponseAlerte.builder()
                .id(alerte.getId())
                .transactionId(alerte.getTransaction() != null ? alerte.getTransaction().getId() : null)
                .referenceTransaction(alerte.getTransaction() != null
                        ? alerte.getTransaction().getReferenceTransaction() : null)
                .regleId(alerte.getRegle() != null ? alerte.getRegle().getId() : null)
                .nomRegle(alerte.getRegle() != null ? alerte.getRegle().getNom() : null)
                .message(alerte.getMessage())
                .niveau(alerte.getNiveau())
                .dateCreation(alerte.getDateCreation())
                .acquittee(alerte.isAcquittee())
                .acquitteePar(alerte.getAcquitteePar())
                .acquitteeLe(alerte.getAcquitteeLe())
                .emailEnvoye(alerte.isEmailEnvoye())
                .emailEnvoyeLe(alerte.getEmailEnvoyeLe())
                .emailDestinataire(alerte.getEmailDestinataire())
                .delaiMinutes(alerte.getDelaiMinutes())
                .build();
    }

    // Consultation

    /**
     * Recherche une transaction par son identifiant.
     *
     * @param id l'identifiant de la transaction
     * @return un {@link Optional} contenant la transaction si trouvée
     */
    public Optional<Transaction> getParId(Long id) {
        return transactionRepository.findById(id);
    }

    /**
     * Recherche une transaction par sa référence.
     *
     * @param reference la référence (format BNA-YYYYMMDD-XXXX)
     * @return un {@link Optional} contenant la transaction si trouvée
     */
    public Optional<Transaction> getParReference(String reference) {
        return transactionRepository.findByReferenceTransaction(reference);
    }

    /**
     * Recherche les transactions avec filtrage dynamique et pagination.
     * <p>
     * Les critères de filtrage sont passés sous forme de paramètres
     * et combinés dans une {@link Specification} JPA.
     * </p>
     *
     * @param statut       le statut (optionnel)
     * @param codeDevise   le code devise (optionnel)
     * @param canal        le canal (optionnel)
     * @param typeTransaction le type de transaction (optionnel)
     * @param minMontant   le montant minimum (optionnel)
     * @param maxMontant   le montant maximum (optionnel)
     * @param dateDebut    la date de début (optionnel)
     * @param dateFin      la date de fin (optionnel)
     * @param pageable     la pagination
     * @return une page de transactions correspondant aux critères
     */
    public Page<Transaction> rechercher(String statut, String codeDevise, String canal,
                                         String typeTransaction, BigDecimal minMontant,
                                         BigDecimal maxMontant, LocalDateTime dateDebut,
                                         LocalDateTime dateFin, Pageable pageable) {

        Specification<Transaction> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filtre par statut
            if (statut != null && !statut.isEmpty()) {
                try {
                    StatutTransaction statutEnum = StatutTransaction.valueOf(statut.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("statut"), statutEnum));
                } catch (IllegalArgumentException e) {
                    log.debug("Statut invalide ignoré : {}", statut);
                }
            }

            // Filtre par devise
            if (codeDevise != null && !codeDevise.isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("devise").get("code"), codeDevise.toUpperCase()));
            }

            // Filtre par canal
            if (canal != null && !canal.isEmpty()) {
                try {
                    Transaction.Canal canalEnum = Transaction.Canal.valueOf(canal.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("canal"), canalEnum));
                } catch (IllegalArgumentException e) {
                    log.debug("Canal invalide ignoré : {}", canal);
                }
            }

            // Filtre par type de transaction
            if (typeTransaction != null && !typeTransaction.isEmpty()) {
                try {
                    Transaction.TypeTransaction typeEnum =
                            Transaction.TypeTransaction.valueOf(typeTransaction.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("typeTransaction"), typeEnum));
                } catch (IllegalArgumentException e) {
                    log.debug("Type de transaction invalide ignoré : {}", typeTransaction);
                }
            }

            // Filtre par montant minimum
            if (minMontant != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("montant"), minMontant));
            }

            // Filtre par montant maximum
            if (maxMontant != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("montant"), maxMontant));
            }

            // Filtre par date de début
            if (dateDebut != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("dateTransaction"), dateDebut));
            }

            // Filtre par date de fin
            if (dateFin != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("dateTransaction"), dateFin));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        log.debug("Recherche de transactions avec {} critères", 
                (statut != null ? 1 : 0) + (codeDevise != null ? 1 : 0)
                + (canal != null ? 1 : 0) + (typeTransaction != null ? 1 : 0)
                + (minMontant != null ? 1 : 0) + (maxMontant != null ? 1 : 0)
                + (dateDebut != null ? 1 : 0) + (dateFin != null ? 1 : 0));

        return transactionRepository.findAll(spec, pageable);
    }

    /**
     * Récupère toutes les transactions avec pagination.
     *
     * @param pageable la pagination
     * @return une page de transactions
     */
    public Page<Transaction> getToutes(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    // Audit

    /**
     * Vérifie l'intégrité de la piste d'audit d'une transaction.
     *
     * @param transactionId l'identifiant de la transaction
     * @return le résultat de la vérification
     */
    public ServiceAudit.ResultatVerification verifierPisteAudit(Long transactionId) {
        return serviceAudit.verifierChaine(transactionId);
    }
}