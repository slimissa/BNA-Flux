package com.bna.flux.repository;

import com.bna.flux.entity.Utilisateur;
import com.bna.flux.entity.Utilisateur.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour l'entité {@link Utilisateur}.
 * <p>
 * Fournit les opérations CRUD de base ainsi que des méthodes de requête
 * personnalisées pour l'authentification JWT, la gestion des utilisateurs,
 * et le filtrage par rôle et agence.
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ul>
 *   <li>{@link com.bna.flux.config.JwtProvider} — chargement de l'utilisateur
 *       lors de la validation du token JWT.</li>
 *   <li>{@link com.bna.flux.controller.AuthController} — authentification
 *       par email/mot de passe.</li>
 *   <li>Service de gestion des utilisateurs (futur) — création, modification,
 *       désactivation par un ADMIN.</li>
 * </ul>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Le mot de passe n'est jamais retourné directement par les requêtes
 *       (utiliser des projections ou DTO dans la couche service).</li>
 *   <li>Les utilisateurs inactifs ne peuvent pas s'authentifier.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /**
     * Recherche un utilisateur par son email exact.
     * <p>
     * Utilisé lors de l'authentification pour charger l'utilisateur
     * par son identifiant de connexion.
     * </p>
     * <p>
     * L'email est stocké en minuscules dans la base de données.
     * La normalisation en minuscules est effectuée dans la couche service
     * avant l'appel à cette méthode.
     * </p>
     *
     * @param email l'adresse email de l'utilisateur
     * @return un {@link Optional} contenant l'utilisateur si trouvé
     */
    Optional<Utilisateur> findByEmail(String email);

    /**
     * Recherche un utilisateur actif par son email.
     * <p>
     * Utilisé lors de l'authentification pour s'assurer que seuls
     * les utilisateurs actifs peuvent se connecter.
     * </p>
     *
     * @param email l'adresse email de l'utilisateur
     * @return un {@link Optional} contenant l'utilisateur actif si trouvé
     */
    Optional<Utilisateur> findByEmailAndActifTrue(String email);

    /**
     * Vérifie si un email est déjà utilisé par un autre utilisateur.
     * <p>
     * Utilisé lors de la création ou modification d'un utilisateur
     * pour garantir l'unicité de l'email.
     * </p>
     *
     * @param email l'adresse email à vérifier
     * @return {@code true} si l'email existe déjà
     */
    boolean existsByEmail(String email);

    /**
     * Vérifie si un email est utilisé par un autre utilisateur que celui spécifié.
     * <p>
     * Utilisé lors de la modification d'un utilisateur pour permettre
     * de conserver le même email.
     * </p>
     *
     * @param email l'adresse email à vérifier
     * @param id    l'identifiant de l'utilisateur à exclure
     * @return {@code true} si l'email existe pour un autre utilisateur
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Recherche tous les utilisateurs ayant un rôle spécifique.
     *
     * @param role le rôle à filtrer (OPERATEUR, SUPERVISEUR, ADMIN)
     * @return la liste des utilisateurs avec ce rôle, triée par nom
     */
    List<Utilisateur> findByRoleOrderByNomAsc(Role role);

    /**
     * Recherche tous les utilisateurs rattachés à une agence spécifique.
     * <p>
     * Utilisé pour le filtrage des données par agence dans le tableau de bord.
     * </p>
     *
     * @param codeAgence le code agence (3 caractères)
     * @return la liste des utilisateurs de cette agence
     */
    List<Utilisateur> findByCodeAgenceOrderByNomAsc(String codeAgence);

    /**
     * Recherche tous les utilisateurs actifs.
     *
     * @return la liste des utilisateurs actifs, triée par nom
     */
    List<Utilisateur> findByActifTrueOrderByNomAsc();

    /**
     * Recherche tous les utilisateurs inactifs.
     * <p>
     * Utilisé par l'administration pour auditer les comptes désactivés.
     * </p>
     *
     * @return la liste des utilisateurs inactifs
     */
    List<Utilisateur> findByActifFalseOrderByNomAsc();

    /**
     * Compte le nombre d'utilisateurs par rôle.
     * <p>
     * Utilisé par le tableau de bord pour les statistiques.
     * </p>
     *
     * @param role le rôle à compter
     * @return le nombre d'utilisateurs avec ce rôle
     */
    long countByRole(Role role);

    /**
     * Compte le nombre d'utilisateurs actifs.
     *
     * @return le nombre d'utilisateurs actifs
     */
    long countByActifTrue();

    /**
     * Met à jour la date de dernière connexion d'un utilisateur.
     * <p>
     * Appelé après chaque authentification JWT réussie.
     * Utilise une requête native pour éviter de charger l'entité complète
     * et pour ne pas déclencher {@code @PreUpdate} (qui modifierait
     * dateModification).
     * </p>
     *
     * @param email             l'email de l'utilisateur
     * @param derniereConnexion la date et heure de la connexion
     */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.derniereConnexion = :date WHERE u.email = :email")
    void updateDerniereConnexion(@Param("email") String email, @Param("date") LocalDateTime derniereConnexion);

    /**
     * Désactive un utilisateur (suppression logique).
     * <p>
     * L'utilisateur reste en base pour l'historique d'audit mais ne peut
     * plus s'authentifier.
     * </p>
     *
     * @param id l'identifiant de l'utilisateur à désactiver
     */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.actif = false WHERE u.id = :id")
    void desactiver(@Param("id") Long id);

    /**
     * Réactive un utilisateur précédemment désactivé.
     *
     * @param id l'identifiant de l'utilisateur à réactiver
     */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.actif = true WHERE u.id = :id")
    void activer(@Param("id") Long id);

    /**
     * Recherche les utilisateurs dont le nom contient une chaîne (insensible à la casse).
     * <p>
     * Utilisé par l'interface d'administration pour la recherche.
     * </p>
     *
     * @param nom le nom ou partie du nom
     * @return la liste des utilisateurs correspondants
     */
    @Query("SELECT u FROM Utilisateur u WHERE LOWER(u.nom) LIKE LOWER(CONCAT('%', :nom, '%')) ORDER BY u.nom")
    List<Utilisateur> findByNomContainingIgnoreCase(@Param("nom") String nom);

    /**
     * Recherche les utilisateurs par rôle et agence.
     * <p>
     * Combinaison des deux critères pour le filtrage avancé.
     * </p>
     *
     * @param role       le rôle
     * @param codeAgence le code agence
     * @return la liste des utilisateurs correspondants
     */
    List<Utilisateur> findByRoleAndCodeAgenceOrderByNomAsc(Role role, String codeAgence);
}