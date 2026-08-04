package com.bna.flux.repository;

import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.entity.Transaction.TypeTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour l'entité {@link Transaction}.
 * <p>
 * Fournit les opérations CRUD de base, le filtrage dynamique via
 * {@link JpaSpecificationExecutor}, et des requêtes personnalisées
 * pour le pipeline, le tableau de bord, et l'export.
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.service.ServiceTransaction} — soumission
 *       et consultation des transactions.</li>
 *   <li>{@link com.bna.flux.service.pipeline.etape.EtapePersistance} —
 *       sauvegarde finale après le pipeline.</li>
 *   <li>{@link com.bna.flux.service.ServiceTableauBord} — agrégations
 *       pour le tableau de bord (comptages, tendances).</li>
 *   <li>{@link com.bna.flux.controller.TransactionController} —
 *       exposition REST avec filtrage, tri et pagination.</li>
 * </ul>
 *
 * <p><b>Performance :</b></p>
 * <ul>
 *   <li>Les requêtes de filtrage utilisent {@link JpaSpecificationExecutor}
 *       pour construire dynamiquement les critères.</li>
 *   <li>Les requêtes d'agrégation utilisent des projections natives
 *       pour éviter de charger les entités complètes.</li>
 *   <li>Les indexes en base doivent couvrir : statut, date_transaction,
 *       code_devise, rib_source, rib_destination.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    // Recherche par référence

    /**
     * Recherche une transaction par sa référence unique.
     *
     * @param reference la référence (format BNA-YYYYMMDD-XXXX)
     * @return un {@link Optional} contenant la transaction si trouvée
     */
    Optional<Transaction> findByReferenceTransaction(String reference);

    /**
     * Vérifie si une référence existe déjà.
     * <p>
     * Utilisé avant la génération pour éviter les collisions.
     * </p>
     *
     * @param reference la référence à vérifier
     * @return {@code true} si la référence existe
     */
    boolean existsByReferenceTransaction(String reference);

    // Recherche par RIB

    /**
     * Recherche les transactions par RIB source.
     *
     * @param ribSource le RIB émetteur (20 chiffres)
     * @param pageable  pagination
     * @return page de transactions
     */
    Page<Transaction> findByRibSource(String ribSource, Pageable pageable);

    /**
     * Recherche les transactions par RIB destination.
     *
     * @param ribDestination le RIB bénéficiaire (20 chiffres)
     * @param pageable       pagination
     * @return page de transactions
     */
    Page<Transaction> findByRibDestination(String ribDestination, Pageable pageable);

    /**
     * Recherche les transactions impliquant un RIB (source ou destination).
     *
     * @param rib      le RIB à rechercher
     * @param pageable pagination
     * @return page de transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.ribSource = :rib OR t.ribDestination = :rib ORDER BY t.dateTransaction DESC")
    Page<Transaction> findByRibSourceOrRibDestination(@Param("rib") String rib, Pageable pageable);

    // Recherche par statut

    /**
     * Recherche les transactions par statut.
     *
     * @param statut   le statut (ACCEPTE, SURVEILLE, BLOQUE)
     * @param pageable pagination
     * @return page de transactions
     */
    Page<Transaction> findByStatut(StatutTransaction statut, Pageable pageable);

    /**
     * Compte les transactions par statut.
     * <p>
     * Utilisé par le tableau de bord pour les cartes de résumé.
     * </p>
     *
     * @param statut le statut
     * @return le nombre de transactions avec ce statut
     */
    long countByStatut(StatutTransaction statut);

    // Recherche par période

    /**
     * Recherche les transactions dans une période donnée.
     *
     * @param debut    date de début (inclusive)
     * @param fin      date de fin (inclusive)
     * @param pageable pagination
     * @return page de transactions
     */
    Page<Transaction> findByDateTransactionBetween(LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    /**
     * Recherche les transactions par statut et période.
     *
     * @param statut   le statut
     * @param debut    date de début
     * @param fin      date de fin
     * @param pageable pagination
     * @return page de transactions
     */
    Page<Transaction> findByStatutAndDateTransactionBetween(
            StatutTransaction statut, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    // Recherche par devise

    /**
     * Recherche les transactions par code devise.
     *
     * @param codeDevise le code ISO 4217
     * @param pageable   pagination
     * @return page de transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.devise.code = :codeDevise")
    Page<Transaction> findByCodeDevise(@Param("codeDevise") String codeDevise, Pageable pageable);

    // Recherche par canal

    /**
     * Recherche les transactions par canal.
     *
     * @param canal    le canal (AGENCE, DAB, EN_LIGNE, MOBILE)
     * @param pageable pagination
     * @return page de transactions
     */
    Page<Transaction> findByCanal(Canal canal, Pageable pageable);

    /**
     * Compte les transactions par canal.
     *
     * @param canal le canal
     * @return le nombre de transactions sur ce canal
     */
    long countByCanal(Canal canal);

    // Recherche par type

    /**
     * Recherche les transactions par type.
     *
     * @param typeTransaction le type (VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT)
     * @param pageable        pagination
     * @return page de transactions
     */
    Page<Transaction> findByTypeTransaction(TypeTransaction typeTransaction, Pageable pageable);

    // Recherche par score de risque

    /**
     * Recherche les transactions dont le score dépasse un seuil.
     *
     * @param scoreMin score minimum
     * @param pageable pagination
     * @return page de transactions
     */
    Page<Transaction> findByScoreRisqueGreaterThanEqual(BigDecimal scoreMin, Pageable pageable);

    // Recherche par pays d'origine

    /**
     * Recherche les transactions par pays d'origine (après enrichissement).
     *
     * @param paysOrigine le pays d'origine
     * @param pageable    pagination
     * @return page de transactions
     */
    Page<Transaction> findByPaysOrigine(String paysOrigine, Pageable pageable);

    /**
     * Recherche les transactions internationales (pays != Tunisie).
     *
     * @param pageable pagination
     * @return page de transactions internationales
     */
    @Query("SELECT t FROM Transaction t WHERE t.paysOrigine IS NOT NULL AND t.paysOrigine != 'Tunisie' ORDER BY t.dateTransaction DESC")
    Page<Transaction> findTransactionsInternationales(Pageable pageable);

    // Agrégations pour le tableau de bord

    /**
     * Compte les transactions par jour dans une période.
     * <p>
     * Retourne une liste de tableaux [date, count] pour construire
     * le graphique de tendance du tableau de bord.
     * </p>
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return liste de paires [date, nombre]
     */
    @Query(value = "SELECT CAST(t.date_transaction AS DATE) as jour, COUNT(*) as nombre " +
           "FROM transactions t " +
           "WHERE t.date_transaction BETWEEN :debut AND :fin " +
           "GROUP BY CAST(t.date_transaction AS DATE) " +
           "ORDER BY jour",
           nativeQuery = true)
    List<Object[]> countTransactionsParJour(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Compte les transactions par statut dans une période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return liste de paires [statut, nombre]
     */
    @Query("SELECT t.statut, COUNT(t) FROM Transaction t " +
           "WHERE t.dateTransaction BETWEEN :debut AND :fin " +
           "GROUP BY t.statut")
    List<Object[]> countByStatutParPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Compte les transactions par canal dans une période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return liste de paires [canal, nombre]
     */
    @Query("SELECT t.canal, COUNT(t) FROM Transaction t " +
           "WHERE t.dateTransaction BETWEEN :debut AND :fin " +
           "GROUP BY t.canal")
    List<Object[]> countByCanalParPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Calcule le montant total des transactions par devise dans une période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return liste de paires [codeDevise, montantTotal]
     */
    @Query("SELECT t.devise.code, SUM(t.montant) FROM Transaction t " +
           "WHERE t.dateTransaction BETWEEN :debut AND :fin " +
           "GROUP BY t.devise.code")
    List<Object[]> sumMontantParDevise(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Récupère le score de risque moyen par période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return le score moyen (peut être null si aucune transaction)
     */
    @Query("SELECT AVG(t.scoreRisque) FROM Transaction t " +
           "WHERE t.dateTransaction BETWEEN :debut AND :fin")
    BigDecimal scoreRisqueMoyen(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Recherche par date pour le pipeline

    /**
     * Recherche les transactions récentes (utile pour vérifier les doublons
     * ou les patterns suspects dans une fenêtre courte).
     *
     * @param ribSource RIB source
     * @param depuis    date à partir de laquelle chercher
     * @return liste des transactions récentes pour ce RIB
     */
    List<Transaction> findByRibSourceAndDateTransactionAfterOrderByDateTransactionDesc(
            String ribSource, LocalDateTime depuis);

    /**
     * Compte les transactions récentes pour un RIB source.
     * <p>
     * Utilisé par le disjoncteur pour détecter des patterns suspects.
     * </p>
     *
     * @param ribSource RIB source
     * @param depuis    date à partir de laquelle compter
     * @return le nombre de transactions
     */
    long countByRibSourceAndDateTransactionAfter(String ribSource, LocalDateTime depuis);
}