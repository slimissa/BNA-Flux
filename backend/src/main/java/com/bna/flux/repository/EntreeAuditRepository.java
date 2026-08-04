package com.bna.flux.repository;

import com.bna.flux.entity.EntreeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository Spring Data JPA pour l'entité {@link EntreeAudit}.
 * <p>
 * Fournit les opérations de lecture pour la piste d'audit hash-chaînée.
 * Les écritures sont effectuées par {@link com.bna.flux.service.ServiceAudit}
 * qui gère le calcul des hashs et l'intégrité de la chaîne.
 * </p>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Les entrées d'audit sont immuables : aucune méthode de mise à jour
 *       ou de suppression n'est exposée.</li>
 *   <li>La lecture se fait toujours par transaction, dans l'ordre
 *       chronologique (horodatage ASC) pour la vérification de la chaîne.</li>
 *   <li>La vérification de la chaîne recalcule tous les hashs et compare
 *       avec les valeurs stockées — voir {@link com.bna.flux.service.ServiceAudit#verifierChaine(Long)}.</li>
 * </ul>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.service.ServiceAudit} — création d'entrées
 *       avec calcul de hash.</li>
 *   <li>{@link com.bna.flux.controller.TransactionController} — consultation
 *       de la piste d'audit et vérification de l'intégrité.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface EntreeAuditRepository extends JpaRepository<EntreeAudit, Long> {

    // Lecture par transaction (pour la piste d'audit)

    /**
     * Récupère toutes les entrées d'audit d'une transaction, dans l'ordre
     * chronologique.
     * <p>
     * L'ordre est essentiel pour la vérification de la chaîne de hachage :
     * chaque entrée référence le hash de l'entrée précédente.
     * </p>
     *
     * @param transactionId l'identifiant de la transaction
     * @return la liste des entrées d'audit, triées par horodatage ASC
     */
    List<EntreeAudit> findByTransactionIdOrderByHorodatageAsc(Long transactionId);

    /**
     * Compte le nombre d'entrées d'audit pour une transaction.
     * <p>
     * Une transaction traitée normalement par le pipeline complet
     * génère exactement 5 entrées (une par étape).
     * </p>
     *
     * @param transactionId l'identifiant de la transaction
     * @return le nombre d'entrées d'audit
     */
    long countByTransactionId(Long transactionId);

    // Recherche par étape du pipeline

    /**
     * Récupère les entrées d'audit d'une transaction pour une étape spécifique.
     *
     * @param transactionId l'identifiant de la transaction
     * @param etape         l'étape du pipeline (VALIDATION, ENRICHISSEMENT, etc.)
     * @return la liste des entrées pour cette étape
     */
    List<EntreeAudit> findByTransactionIdAndEtapeOrderByHorodatageAsc(Long transactionId, String etape);

    /**
     * Vérifie si une transaction a déjà une entrée d'audit pour une étape donnée.
     * <p>
     * Utilisé pour éviter les doublons si une étape est ré-exécutée.
     * </p>
     *
     * @param transactionId l'identifiant de la transaction
     * @param etape         l'étape du pipeline
     * @return {@code true} si une entrée existe déjà pour cette étape
     */
    boolean existsByTransactionIdAndEtape(Long transactionId, String etape);

    // Recherche par opérateur

    /**
     * Récupère les entrées d'audit créées par un opérateur spécifique
     * (hors "SYSTEME").
     * <p>
     * Utilisé pour auditer les actions manuelles des utilisateurs.
     * </p>
     *
     * @param operateur l'identifiant de l'opérateur
     * @return la liste des entrées créées par cet opérateur
     */
    List<EntreeAudit> findByOperateurOrderByHorodatageDesc(String operateur);

    /**
     * Récupère les entrées d'audit créées par le système (pipeline automatique).
     *
     * @param transactionId l'identifiant de la transaction
     * @return la liste des entrées système
     */
    List<EntreeAudit> findByTransactionIdAndOperateurOrderByHorodatageAsc(Long transactionId, String operateur);

    // Recherche par période

    /**
     * Récupère les entrées d'audit dans une période donnée.
     * <p>
     * Utilisé pour les exports et les rapports de conformité.
     * </p>
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return la liste des entrées dans la période
     */
    List<EntreeAudit> findByHorodatageBetweenOrderByHorodatageAsc(LocalDateTime debut, LocalDateTime fin);

    /**
     * Récupère les entrées d'audit par transaction et période.
     *
     * @param transactionId l'identifiant de la transaction
     * @param debut         date de début
     * @param fin           date de fin
     * @return la liste des entrées correspondantes
     */
    List<EntreeAudit> findByTransactionIdAndHorodatageBetweenOrderByHorodatageAsc(
            Long transactionId, LocalDateTime debut, LocalDateTime fin);

    // Vérification de l'intégrité de la chaîne

    /**
     * Récupère la dernière entrée d'audit pour une transaction
     * (celle avec l'horodatage le plus récent).
     * <p>
     * Utilisé pour obtenir le dernier hash de la chaîne avant d'ajouter
     * une nouvelle entrée.
     * </p>
     *
     * @param transactionId l'identifiant de la transaction
     * @return la dernière entrée d'audit, ou {@code null} si aucune
     */
    @Query("SELECT a FROM EntreeAudit a WHERE a.transaction.id = :transactionId " +
           "ORDER BY a.horodatage DESC LIMIT 1")
    EntreeAudit findLastByTransactionId(@Param("transactionId") Long transactionId);

    /**
     * Compte le nombre total d'entrées d'audit dans le système.
     *
     * @return le nombre total d'entrées
     */
    long count();

    /**
     * Récupère les entrées d'audit pour une action spécifique.
     * <p>
     * Utile pour rechercher toutes les occurrences d'un type d'action
     * (ex: "TRANSACTION_BLOQUEE", "DISJONCTEUR_OUVERT").
     * </p>
     *
     * @param action l'action recherchée
     * @return la liste des entrées correspondantes
     */
    List<EntreeAudit> findByActionOrderByHorodatageDesc(String action);
}