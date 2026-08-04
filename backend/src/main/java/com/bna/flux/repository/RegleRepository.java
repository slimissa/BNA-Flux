package com.bna.flux.repository;

import com.bna.flux.entity.Regle;
import com.bna.flux.entity.Regle.Severite;
import com.bna.flux.entity.Regle.TypeRegle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour l'entité {@link Regle}.
 * <p>
 * Fournit les opérations CRUD de base ainsi que des méthodes de requête
 * personnalisées pour le moteur de règles et l'interface d'administration.
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.service.MoteurRegles} — chargement des règles
 *       actives pour évaluation dans le pipeline (Stage 3).</li>
 *   <li>{@link com.bna.flux.service.ServiceRegle} — CRUD et gestion
 *       des règles (activation, désactivation, test).</li>
 *   <li>{@link com.bna.flux.controller.RegleController} — exposition REST
 *       pour l'interface d'administration.</li>
 * </ul>
 *
 * <p><b>Performance :</b></p>
 * <ul>
 *   <li>Les requêtes de lecture pour le pipeline utilisent un cache
 *       au niveau du service ({@code MoteurRegles}) pour éviter de
 *       recharger les règles à chaque transaction.</li>
 *   <li>Les requêtes d'écriture sont protégées par le versionnement
 *       optimiste de l'entité parente (pas de {@code @Version} sur Regle).</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface RegleRepository extends JpaRepository<Regle, Long> {

    // Requêtes pour le pipeline (Stage 3 — Évaluation des règles)

    /**
     * Charge toutes les règles actives, triées par priorité décroissante
     * puis par sévérité (CRITIQUE en premier).
     * <p>
     * C'est la requête principale utilisée par le moteur de règles lors
     * du passage de chaque transaction dans le pipeline.
     * </p>
     * <p>
     * L'ordre d'évaluation est critique :
     * </p>
     * <ol>
     *   <li>Priorité 0 (la plus haute) en premier</li>
     *   <li>En cas d'égalité, les règles CRITIQUE et AUTO_REJET d'abord</li>
     *   <li>Puis ELEVE, MOYEN, FAIBLE</li>
     * </ol>
     *
     * @return la liste des règles actives dans l'ordre d'évaluation
     */
    @Query("SELECT r FROM Regle r WHERE r.actif = true ORDER BY r.priorite ASC, " +
           "CASE r.severite " +
           "  WHEN com.bna.flux.entity.Regle$Severite.CRITIQUE THEN 0 " +
           "  WHEN com.bna.flux.entity.Regle$Severite.ELEVE THEN 1 " +
           "  WHEN com.bna.flux.entity.Regle$Severite.MOYEN THEN 2 " +
           "  WHEN com.bna.flux.entity.Regle$Severite.FAIBLE THEN 3 " +
           "END")
    List<Regle> findActiveRulesForEvaluation();

    /**
     * Charge uniquement les règles actives de type AUTO_REJET.
     * <p>
     * Ces règles sont évaluées en priorité car elles peuvent bloquer
     * la transaction immédiatement, sans attendre l'évaluation complète.
     * </p>
     *
     * @return la liste des règles d'auto-rejet actives
     */
    List<Regle> findByActifTrueAndTypeRegleOrderByPrioriteAsc(TypeRegle typeRegle);

    /**
     * Compte le nombre de règles actives.
     * <p>
     * Utilisé par le tableau de bord pour les statistiques.
     * </p>
     *
     * @return le nombre de règles actives
     */
    long countByActifTrue();

    /**
     * Compte le nombre de règles actives par sévérité.
     *
     * @param severite le niveau de sévérité
     * @return le nombre de règles avec cette sévérité
     */
    long countByActifTrueAndSeverite(Severite severite);

    // Requêtes pour l'administration

    /**
     * Recherche toutes les règles, actives ou inactives, triées par priorité.
     *
     * @return la liste complète des règles
     */
    List<Regle> findAllByOrderByPrioriteAsc();

    /**
     * Recherche les règles par sévérité.
     *
     * @param severite le niveau de sévérité
     * @return la liste des règles correspondantes
     */
    List<Regle> findBySeveriteOrderByPrioriteAsc(Severite severite);

    /**
     * Recherche les règles par type.
     *
     * @param typeRegle le type de règle
     * @return la liste des règles correspondantes
     */
    List<Regle> findByTypeRegleOrderByPrioriteAsc(TypeRegle typeRegle);

    /**
     * Recherche les règles par catégorie.
     *
     * @param categorie la catégorie fonctionnelle
     * @return la liste des règles correspondantes
     */
    List<Regle> findByCategorieOrderByPrioriteAsc(String categorie);

    /**
     * Recherche des règles par nom (partiel, insensible à la casse).
     *
     * @param nom le nom ou partie du nom
     * @return la liste des règles correspondantes
     */
    @Query("SELECT r FROM Regle r WHERE LOWER(r.nom) LIKE LOWER(CONCAT('%', :nom, '%')) ORDER BY r.priorite ASC")
    List<Regle> findByNomContainingIgnoreCase(@Param("nom") String nom);

    /**
     * Recherche les règles par sévérité et type.
     *
     * @param severite  le niveau de sévérité
     * @param typeRegle le type de règle
     * @return la liste des règles correspondantes
     */
    List<Regle> findBySeveriteAndTypeRegleOrderByPrioriteAsc(Severite severite, TypeRegle typeRegle);

    /**
     * Vérifie si une règle avec ce nom existe déjà.
     * <p>
     * Utilisé pour éviter les doublons lors de la création.
     * </p>
     *
     * @param nom le nom de la règle
     * @return {@code true} si une règle avec ce nom existe
     */
    boolean existsByNom(String nom);

    /**
     * Vérifie si une règle avec ce nom existe, en excluant un ID.
     * <p>
     * Utilisé lors de la modification pour permettre de conserver le même nom.
     * </p>
     *
     * @param nom le nom de la règle
     * @param id  l'identifiant à exclure
     * @return {@code true} si une autre règle porte ce nom
     */
    boolean existsByNomAndIdNot(String nom, Long id);

    // Opérations d'activation/désactivation

    /**
     * Active une règle par son ID.
     *
     * @param id l'identifiant de la règle
     */
    @Modifying
    @Query("UPDATE Regle r SET r.actif = true WHERE r.id = :id")
    void activer(@Param("id") Long id);

    /**
     * Désactive une règle par son ID.
     *
     * @param id l'identifiant de la règle
     */
    @Modifying
    @Query("UPDATE Regle r SET r.actif = false WHERE r.id = :id")
    void desactiver(@Param("id") Long id);

    /**
     * Bascule l'état actif/inactif d'une règle.
     *
     * @param id l'identifiant de la règle
     */
    @Modifying
    @Query("UPDATE Regle r SET r.actif = NOT r.actif WHERE r.id = :id")
    void basculer(@Param("id") Long id);

    // Statistiques pour le tableau de bord

    /**
     * Compte le nombre de règles par catégorie.
     *
     * @param categorie la catégorie
     * @return le nombre de règles dans cette catégorie
     */
    long countByCategorie(String categorie);

    /**
     * Récupère les catégories distinctes de règles.
     *
     * @return la liste des catégories distinctes
     */
    @Query("SELECT DISTINCT r.categorie FROM Regle r WHERE r.categorie IS NOT NULL ORDER BY r.categorie")
    List<String> findDistinctCategories();
}