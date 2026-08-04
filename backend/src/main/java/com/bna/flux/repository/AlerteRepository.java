package com.bna.flux.repository;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Alerte.NiveauAlerte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository Spring Data JPA pour l'entité {@link Alerte}.
 * <p>
 * Fournit les opérations CRUD de base ainsi que des méthodes de requête
 * personnalisées pour la gestion des alertes, l'envoi d'emails, et les
 * statistiques du tableau de bord.
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.service.pipeline.etape.EtapePersistance} —
 *       sauvegarde des alertes générées par le pipeline.</li>
 *   <li>{@link com.bna.flux.service.ServiceEmail} — recherche des alertes
 *       en attente d'envoi d'email.</li>
 *   <li>{@link com.bna.flux.controller.AlerteController} — consultation
 *       et acquittement des alertes.</li>
 *   <li>{@link com.bna.flux.service.ServiceTableauBord} — comptage
 *       des alertes par sévérité pour le tableau de bord.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    // Recherche par transaction

    /**
     * Recherche toutes les alertes liées à une transaction spécifique.
     * <p>
     * Utilisé pour afficher la liste des alertes dans le détail
     * d'une transaction.
     * </p>
     *
     * @param transactionId l'identifiant de la transaction
     * @return la liste des alertes, triées par date de création décroissante
     */
    List<Alerte> findByTransactionIdOrderByDateCreationDesc(Long transactionId);

    /**
     * Compte le nombre d'alertes pour une transaction.
     *
     * @param transactionId l'identifiant de la transaction
     * @return le nombre d'alertes
     */
    long countByTransactionId(Long transactionId);

    // Recherche par règle

    /**
     * Recherche les alertes déclenchées par une règle spécifique.
     * <p>
     * Utilisé pour analyser l'efficacité d'une règle.
     * </p>
     *
     * @param regleId  l'identifiant de la règle
     * @param pageable pagination
     * @return page d'alertes
     */
    Page<Alerte> findByRegleId(Long regleId, Pageable pageable);

    /**
     * Compte le nombre de déclenchements par règle dans une période.
     * <p>
     * Utilisé par le tableau de bord pour afficher le "Top 5 des règles
     * les plus déclenchées".
     * </p>
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return liste de paires [regleId, regleNom, nombre]
     */
    @Query("SELECT a.regle.id, a.regle.nom, COUNT(a) FROM Alerte a " +
           "WHERE a.dateCreation BETWEEN :debut AND :fin " +
           "GROUP BY a.regle.id, a.regle.nom " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> countDeclenchementsParRegle(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Recherche par niveau de sévérité

    /**
     * Recherche les alertes par niveau de sévérité.
     *
     * @param niveau   le niveau (FAIBLE, MOYEN, ELEVE, CRITIQUE)
     * @param pageable pagination
     * @return page d'alertes
     */
    Page<Alerte> findByNiveau(NiveauAlerte niveau, Pageable pageable);

    /**
     * Recherche les alertes de niveau ELEVE ou CRITIQUE (non acquittées).
     * <p>
     * Utilisé pour le tableau de bord "Actions requises".
     * </p>
     *
     * @return la liste des alertes critiques non traitées
     */
    @Query("SELECT a FROM Alerte a WHERE a.acquittee = false AND a.niveau IN ('ELEVE', 'CRITIQUE') " +
           "ORDER BY CASE a.niveau WHEN 'CRITIQUE' THEN 0 WHEN 'ELEVE' THEN 1 END, a.dateCreation ASC")
    List<Alerte> findAlertesNonAcquitteesPrioritaires();

    /**
     * Compte les alertes par niveau dans une période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return liste de paires [niveau, nombre]
     */
    @Query("SELECT a.niveau, COUNT(a) FROM Alerte a " +
           "WHERE a.dateCreation BETWEEN :debut AND :fin " +
           "GROUP BY a.niveau")
    List<Object[]> countByNiveauParPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Compte les alertes non acquittées par niveau.
     *
     * @param niveau le niveau d'alerte
     * @return le nombre d'alertes non acquittées de ce niveau
     */
    long countByNiveauAndAcquitteeFalse(NiveauAlerte niveau);

    // Recherche par statut d'acquittement

    /**
     * Recherche les alertes non acquittées.
     *
     * @param pageable pagination
     * @return page d'alertes non acquittées
     */
    Page<Alerte> findByAcquitteeFalse(Pageable pageable);

    /**
     * Recherche les alertes acquittées.
     *
     * @param pageable pagination
     * @return page d'alertes acquittées
     */
    Page<Alerte> findByAcquitteeTrue(Pageable pageable);

    /**
     * Compte les alertes non acquittées.
     *
     * @return le nombre total d'alertes en attente
     */
    long countByAcquitteeFalse();

    // Recherche pour l'envoi d'emails

    /**
     * Recherche les alertes CRITIQUE non acquittées et dont l'email
     * n'a pas encore été envoyé.
     * <p>
     * Utilisé par le service d'envoi d'emails immédiats.
     * </p>
     *
     * @return la liste des alertes en attente d'email immédiat
     */
    @Query("SELECT a FROM Alerte a WHERE a.niveau = 'CRITIQUE' AND a.emailEnvoye = false " +
           "ORDER BY a.dateCreation ASC")
    List<Alerte> findAlertesEnAttenteEmailImmediat();

    /**
     * Recherche les alertes ELEVE non acquittées et dont l'email
     * n'a pas encore été envoyé (pour envoi groupé toutes les 15 minutes).
     *
     * @return la liste des alertes en attente d'email groupé
     */
    @Query("SELECT a FROM Alerte a WHERE a.niveau = 'ELEVE' AND a.emailEnvoye = false " +
           "ORDER BY a.dateCreation ASC")
    List<Alerte> findAlertesEnAttenteEmailGroupe();

    /**
     * Marque une alerte comme ayant reçu son email.
     *
     * @param id           l'identifiant de l'alerte
     * @param destinataire l'adresse email du destinataire
     */
    @Modifying
    @Query("UPDATE Alerte a SET a.emailEnvoye = true, a.emailEnvoyeLe = CURRENT_TIMESTAMP, " +
           "a.emailDestinataire = :destinataire WHERE a.id = :id")
    void marquerEmailEnvoye(@Param("id") Long id, @Param("destinataire") String destinataire);

    // Recherche par période

    /**
     * Recherche les alertes dans une période donnée.
     *
     * @param debut    date de début
     * @param fin      date de fin
     * @param pageable pagination
     * @return page d'alertes
     */
    Page<Alerte> findByDateCreationBetween(LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    /**
     * Recherche les alertes par niveau et période.
     *
     * @param niveau   le niveau
     * @param debut    date de début
     * @param fin      date de fin
     * @param pageable pagination
     * @return page d'alertes
     */
    Page<Alerte> findByNiveauAndDateCreationBetween(
            NiveauAlerte niveau, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    // Acquittement

    /**
     * Acquitte une alerte.
     *
     * @param id           l'identifiant de l'alerte
     * @param acquitteePar l'identifiant de l'opérateur
     */
    @Modifying
    @Query("UPDATE Alerte a SET a.acquittee = true, a.acquitteePar = :operateur, " +
           "a.acquitteeLe = CURRENT_TIMESTAMP WHERE a.id = :id AND a.acquittee = false")
    void acquitter(@Param("id") Long id, @Param("operateur") String acquitteePar);

    // Statistiques

    /**
     * Compte le nombre total d'alertes dans une période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return le nombre total d'alertes
     */
    long countByDateCreationBetween(LocalDateTime debut, LocalDateTime fin);

    /**
     * Calcule le nombre moyen d'alertes par transaction dans une période.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return le nombre moyen d'alertes par transaction
     */
    @Query(value = "SELECT AVG(alert_count) FROM " +
           "(SELECT COUNT(*) as alert_count FROM alertes a " +
           "WHERE a.date_creation BETWEEN :debut AND :fin " +
           "GROUP BY a.transaction_id) sub",
           nativeQuery = true)
    Double moyenneAlertesParTransaction(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Récupère le délai moyen d'acquittement en minutes.
     *
     * @param debut date de début
     * @param fin   date de fin
     * @return le délai moyen en minutes (peut être null)
     */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (a.acquittee_le - a.date_creation)) / 60) " +
           "FROM alertes a WHERE a.acquittee = true " +
           "AND a.date_creation BETWEEN :debut AND :fin",
           nativeQuery = true)
    Double delaiMoyenAcquittementMinutes(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}