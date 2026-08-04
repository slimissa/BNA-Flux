package com.bna.flux.repository;

import com.bna.flux.entity.Devise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour l'entité {@link Devise}.
 * <p>
 * Fournit les opérations CRUD de base via {@link JpaRepository} ainsi que
 * des méthodes de requête personnalisées pour la recherche et le filtrage
 * des devises.
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.service.InitialisateurDevises} — chargement initial
 *       des devises depuis {@code devises.json} au démarrage.</li>
 *   <li>{@link com.bna.flux.service.pipeline.etape.EtapeValidation} — validation
 *       du code devise d'une transaction au Stage 1 du pipeline.</li>
 *   <li>{@link com.bna.flux.controller.DeviseController} — exposition REST
 *       publique (sans authentification) de la liste des devises.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface DeviseRepository extends JpaRepository<Devise, String> {

    /**
     * Recherche une devise par son code ISO 4217 exact.
     * <p>
     * Le code est insensible à la casse grâce au {@code UPPER} dans la requête.
     * </p>
     *
     * @param code le code devise (ex: "TND", "EUR", "USD")
     * @return un {@link Optional} contenant la devise si trouvée
     */
    @Query("SELECT d FROM Devise d WHERE UPPER(d.code) = UPPER(:code)")
    Optional<Devise> findByCodeIgnoreCase(@Param("code") String code);

    /**
     * Recherche toutes les devises actives.
     * <p>
     * Utilisé par :
     * </p>
     * <ul>
     *   <li>Le contrôleur REST pour exposer la liste des devises utilisables</li>
     *   <li>La validation des transactions pour vérifier qu'une devise est active</li>
     * </ul>
     *
     * @return la liste des devises actives, triée par code
     */
    List<Devise> findByActifTrueOrderByCodeAsc();

    /**
     * Recherche toutes les devises, actives ou inactives.
     * <p>
     * Utilisé par l'administration pour la gestion complète des devises.
     * </p>
     *
     * @return la liste complète triée par code
     */
    List<Devise> findAllByOrderByCodeAsc();

    /**
     * Vérifie si une devise existe avec ce code.
     * <p>
     * Plus performant que {@code findById().isPresent()} car ne charge pas l'entité.
     * </p>
     *
     * @param code le code devise
     * @return {@code true} si une devise avec ce code existe
     */
    boolean existsByCode(String code);

    /**
     * Vérifie si une devise active existe avec ce code.
     * <p>
     * Utilisé par la validation des transactions pour rejeter les devises inactives.
     * </p>
     *
     * @param code le code devise
     * @return {@code true} si une devise active avec ce code existe
     */
    boolean existsByCodeAndActifTrue(String code);

    /**
     * Recherche des devises par nom (recherche partielle, insensible à la casse).
     * <p>
     * Utilisé par l'interface d'administration pour la recherche.
     * </p>
     *
     * @param nom le nom ou partie du nom de la devise
     * @return la liste des devises correspondantes
     */
    @Query("SELECT d FROM Devise d WHERE LOWER(d.nom) LIKE LOWER(CONCAT('%', :nom, '%')) ORDER BY d.code")
    List<Devise> findByNomContainingIgnoreCase(@Param("nom") String nom);

    /**
     * Compte le nombre de devises actives.
     * <p>
     * Utilisé par le tableau de bord pour les statistiques.
     * </p>
     *
     * @return le nombre de devises actives
     */
    long countByActifTrue();

    /**
     * Recherche une devise par son code numérique ISO 4217.
     *
     * @param codeNumerique le code numérique à 3 chiffres (ex: "788" pour TND)
     * @return un {@link Optional} contenant la devise si trouvée
     */
    Optional<Devise> findByCodeNumerique(String codeNumerique);

    /**
     * Recherche les devises ayant un certain nombre d'unités mineures.
     * <p>
     * Utile pour les contrôles de cohérence : par exemple, toutes les devises
     * à 3 décimales (TND, KWD, BHD) pour vérifier la précision des montants.
     * </p>
     *
     * @param unitesMineures le nombre d'unités mineures
     * @return la liste des devises correspondantes
     */
    List<Devise> findByUnitesMineures(int unitesMineures);
}