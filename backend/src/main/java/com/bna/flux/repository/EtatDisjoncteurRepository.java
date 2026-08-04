package com.bna.flux.repository;

import com.bna.flux.entity.EtatDisjoncteur;
import com.bna.flux.entity.EtatDisjoncteur.Etat;
import com.bna.flux.entity.EtatDisjoncteur.TypeCible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour l'entité {@link EtatDisjoncteur}.
 * <p>
 * Fournit les opérations CRUD de base ainsi que des méthodes de requête
 * personnalisées pour la gestion des disjoncteurs (Circuit Breakers).
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.service.ServiceDisjoncteur} — gestion
 *       des transitions d'état, enregistrement des échecs, réinitialisation.</li>
 *   <li>{@link com.bna.flux.service.pipeline.etape.EtapeValidation} —
 *       vérification de l'état du disjoncteur avant d'autoriser une transaction
 *       (Stage 1 du pipeline).</li>
 *   <li>{@link com.bna.flux.service.pipeline.etape.EtapeNotation} —
 *       enregistrement des échecs et ouverture des disjoncteurs (Stage 4).</li>
 *   <li>{@link com.bna.flux.controller.DisjoncteurController} — consultation
 *       et réinitialisation manuelle par un SUPERVISEUR ou ADMIN.</li>
 * </ul>
 *
 * <p><b>Performance :</b></p>
 * <ul>
 *   <li>La recherche par (typeCible, identifiantCible) est la plus fréquente
 *       car exécutée à chaque transaction (Stage 1).</li>
 *   <li>Un index composite sur (type_cible, identifiant_cible) est recommandé
 *       en production.</li>
 *   <li>Les disjoncteurs ouverts sont vérifiés périodiquement pour le passage
 *       automatique en MI_OUVERT après expiration du délai.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface EtatDisjoncteurRepository extends JpaRepository<EtatDisjoncteur, Long> {

    // Recherche par cible (utilisé à chaque transaction — Stage 1)

    /**
     * Recherche le disjoncteur correspondant à une cible spécifique.
     * <p>
     * Un disjoncteur est identifié de manière unique par le couple
     * (typeCible, identifiantCible). Si aucun disjoncteur n'existe
     * pour cette cible, cela signifie qu'aucun échec n'a encore été
     * enregistré — le circuit est considéré comme FERMÉ.
     * </p>
     *
     * @param typeCible        le type de cible (COMPTE_SOURCE, COMPTE_DESTINATION, AGENCE, CANAL)
     * @param identifiantCible l'identifiant de la cible (RIB, code agence, canal)
     * @return un {@link Optional} contenant le disjoncteur s'il existe
     */
    Optional<EtatDisjoncteur> findByTypeCibleAndIdentifiantCible(TypeCible typeCible, String identifiantCible);

    /**
     * Vérifie si un disjoncteur existe pour une cible donnée.
     * <p>
     * Plus performant que {@code findBy...().isPresent()} car ne charge pas l'entité.
     * </p>
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     * @return {@code true} si un disjoncteur existe
     */
    boolean existsByTypeCibleAndIdentifiantCible(TypeCible typeCible, String identifiantCible);

    // Recherche par état (pour la surveillance et les transitions automatiques)

    /**
     * Recherche tous les disjoncteurs dans un état donné.
     *
     * @param etat l'état (FERME, OUVERT, MI_OUVERT)
     * @return la liste des disjoncteurs dans cet état
     */
    List<EtatDisjoncteur> findByEtat(Etat etat);

    /**
     * Recherche tous les disjoncteurs actuellement ouverts.
     * <p>
     * Utilisé par le tableau de bord pour afficher les disjoncteurs
     * qui bloquent actuellement des transactions.
     * </p>
     *
     * @return la liste des disjoncteurs ouverts
     */
    List<EtatDisjoncteur> findByEtatOrderByDateDerniereOuvertureDesc(Etat etat);

    /**
     * Recherche les disjoncteurs ouverts depuis plus longtemps que le délai
     * configuré — candidats au passage automatique en MI_OUVERT.
     * <p>
     * Appelé périodiquement par une tâche planifiée pour vérifier si des
     * disjoncteurs doivent passer en phase de test.
     * </p>
     *
     * @param dateLimite la date limite : les disjoncteurs ouverts avant cette
     *                   date sont éligibles au passage en MI_OUVERT
     * @return la liste des disjoncteurs éligibles
     */
    @Query("SELECT d FROM EtatDisjoncteur d WHERE d.etat = 'OUVERT' " +
           "AND d.dateDerniereOuverture IS NOT NULL " +
           "AND d.dateDerniereOuverture < :dateLimite")
    List<EtatDisjoncteur> findDisjoncteursOuvertsEligiblesMiOuvert(@Param("dateLimite") LocalDateTime dateLimite);

    /**
     * Compte le nombre de disjoncteurs par état.
     *
     * @param etat l'état
     * @return le nombre de disjoncteurs dans cet état
     */
    long countByEtat(Etat etat);

    // Recherche par type de cible

    /**
     * Recherche tous les disjoncteurs d'un type de cible donné.
     *
     * @param typeCible le type de cible
     * @return la liste des disjoncteurs de ce type
     */
    List<EtatDisjoncteur> findByTypeCible(TypeCible typeCible);

    /**
     * Recherche les disjoncteurs par type de cible et état.
     *
     * @param typeCible le type de cible
     * @param etat      l'état
     * @return la liste des disjoncteurs correspondants
     */
    List<EtatDisjoncteur> findByTypeCibleAndEtat(TypeCible typeCible, Etat etat);

    // Compteurs et statistiques

    /**
     * Compte le nombre total de disjoncteurs par type de cible.
     *
     * @param typeCible le type de cible
     * @return le nombre de disjoncteurs de ce type
     */
    long countByTypeCible(TypeCible typeCible);

    /**
     * Récupère le nombre total d'échecs enregistrés (tous disjoncteurs confondus).
     * <p>
     * Utilisé par le tableau de bord pour les statistiques globales.
     * </p>
     *
     * @return la somme des échecs
     */
    @Query("SELECT COALESCE(SUM(d.nombreEchecs), 0) FROM EtatDisjoncteur d")
    long sumNombreEchecs();

    // Opérations de mise à jour

    /**
     * Réinitialise un disjoncteur à l'état FERMÉ avec compteur d'échecs à zéro.
     *
     * @param id l'identifiant du disjoncteur
     */
    @Modifying
    @Query("UPDATE EtatDisjoncteur d SET d.etat = 'FERME', d.nombreEchecs = 0, " +
           "d.dateDerniereFermeture = CURRENT_TIMESTAMP WHERE d.id = :id")
    void reinitialiser(@Param("id") Long id);

    /**
     * Incrémente le compteur d'échecs d'un disjoncteur.
     * <p>
     * Si le seuil est atteint, le disjoncteur passe à OUVERT.
     * Cette méthode est appelée après chaque transaction BLOQUEE.
     * </p>
     *
     * @param id l'identifiant du disjoncteur
     */
    @Modifying
    @Query("UPDATE EtatDisjoncteur d SET d.nombreEchecs = d.nombreEchecs + 1, " +
           "d.dateDernierEchec = CURRENT_TIMESTAMP, " +
           "d.etat = CASE WHEN (d.nombreEchecs + 1) >= d.seuilEchecs THEN 'OUVERT' ELSE d.etat END, " +
           "d.dateDerniereOuverture = CASE WHEN (d.nombreEchecs + 1) >= d.seuilEchecs " +
           "THEN CURRENT_TIMESTAMP ELSE d.dateDerniereOuverture END " +
           "WHERE d.id = :id")
    void incrementerEchecs(@Param("id") Long id);

    /**
     * Passe un disjoncteur de OUVERT à MI_OUVERT.
     *
     * @param id l'identifiant du disjoncteur
     */
    @Modifying
    @Query("UPDATE EtatDisjoncteur d SET d.etat = 'MI_OUVERT' WHERE d.id = :id AND d.etat = 'OUVERT'")
    void passerEnMiOuvert(@Param("id") Long id);

    /**
     * Passe un disjoncteur de MI_OUVERT à FERMÉ (test réussi).
     *
     * @param id l'identifiant du disjoncteur
     */
    @Modifying
    @Query("UPDATE EtatDisjoncteur d SET d.etat = 'FERME', d.nombreEchecs = 0, " +
           "d.dateDerniereFermeture = CURRENT_TIMESTAMP WHERE d.id = :id AND d.etat = 'MI_OUVERT'")
    void confirmerTestReussi(@Param("id") Long id);

    /**
     * Passe un disjoncteur de MI_OUVERT à OUVERT (test échoué).
     *
     * @param id l'identifiant du disjoncteur
     */
    @Modifying
    @Query("UPDATE EtatDisjoncteur d SET d.etat = 'OUVERT', " +
           "d.nombreEchecs = d.nombreEchecs + 1, " +
           "d.dateDernierEchec = CURRENT_TIMESTAMP, " +
           "d.dateDerniereOuverture = CURRENT_TIMESTAMP " +
           "WHERE d.id = :id AND d.etat = 'MI_OUVERT'")
    void confirmerTestEchoue(@Param("id") Long id);
}